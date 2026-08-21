package com.milki.launcher.presentation.home

import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Identifies why a home model mutation did not apply. Carries the rejected
 * [command] so the UI can own user-facing message text and localization.
 */
sealed interface HomeMutationError {
    /** The writer rejected the command semantically (e.g. position occupied). */
    data class Rejected(val command: HomeModelWriter.Command) : HomeMutationError

    /** Persisting the command threw; [cause] carries the underlying failure. */
    data class Failed(val command: HomeModelWriter.Command, val cause: Exception) : HomeMutationError
}

/**
 * Owns the home model write path:
 * - one mutation lock
 * - one writer
 * - one place that persists updates
 *
 * Pure Kotlin plus [HomeRepository], so it is fully unit-testable without
 * Android dependencies.
 */
class HomeModelMutator(
    private val homeRepository: HomeRepository,
    private val scope: CoroutineScope,
    private val onItemsPersisted: suspend (updatedItems: List<HomeItem>) -> Unit = {}
) {

    private val modelWriter = HomeModelWriter()
    private val mutationMutex = Mutex()
    private val pendingMutationCount = MutableStateFlow(0)
    private val _mutationErrors = Channel<HomeMutationError>(capacity = Channel.BUFFERED)

    val isUpdatingPositions: StateFlow<Boolean> = pendingMutationCount
        .map { pendingUpdates -> pendingUpdates > 0 }
        .stateIn(
            scope = scope,
            started = ViewModelSharingStarted,
            initialValue = false
        )

    /** One-shot mutation failures as events; collect via [receiveAsFlow]. */
    val mutationErrors: Channel<HomeMutationError> = _mutationErrors

    fun mutate(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ) {
        scope.launch {
            applyTracked(command, onApplied)
        }
    }

    /**
     * Applies a command with mutation-count tracking. Returns whether the
     * command was applied; failures are emitted on [mutationErrors].
     * [onFailure] lets callers attach cleanup (e.g. resource deallocation)
     * without re-implementing bookkeeping.
     */
    suspend fun applyTracked(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {},
        onFailure: suspend () -> Unit = {}
    ): Boolean {
        pendingMutationCount.update { it + 1 }

        try {
            val wasApplied = tryApply(command, onApplied)

            if (!wasApplied) {
                _mutationErrors.send(HomeMutationError.Rejected(command))
                onFailure()
            }

            return wasApplied
        } finally {
            pendingMutationCount.update { current -> (current - 1).coerceAtLeast(0) }
        }
    }

    /**
     * Applies a command without mutation-count tracking or error reporting.
     * Exceptions propagate to the caller.
     */
    suspend fun apply(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ): Boolean {
        return mutationMutex.withLock {
            val currentItems = homeRepository.readPinnedItems()
            when (
                val result = modelWriter.apply(
                    currentItems = currentItems,
                    command = command
                )
            ) {
                is HomeModelWriter.Result.Applied -> {
                    persistUpdatedItems(currentItems, result.items)
                    onApplied(result.items)
                    true
                }

                is HomeModelWriter.Result.Rejected -> false
            }
        }
    }

    private suspend fun tryApply(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit
    ): Boolean {
        return try {
            apply(command, onApplied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _mutationErrors.send(HomeMutationError.Failed(command, e))
            false
        }
    }

    private suspend fun persistUpdatedItems(
        currentItems: List<HomeItem>,
        updatedItems: List<HomeItem>
    ) {
        if (updatedItems == currentItems) {
            return
        }

        homeRepository.replacePinnedItems(updatedItems)
        onItemsPersisted(updatedItems)
    }
}
