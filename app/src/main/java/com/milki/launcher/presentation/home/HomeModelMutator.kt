package com.milki.launcher.presentation.home

import com.milki.launcher.presentation.common.ViewModelSharingStarted
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val _lastMoveErrorMessage = MutableStateFlow<String?>(null)

    val isUpdatingPositions: StateFlow<Boolean> = pendingMutationCount
        .map { pendingUpdates -> pendingUpdates > 0 }
        .stateIn(
            scope = scope,
            started = ViewModelSharingStarted,
            initialValue = false
        )

    val lastMoveErrorMessage: StateFlow<String?> = _lastMoveErrorMessage

    fun clearMoveError() {
        _lastMoveErrorMessage.value = null
    }

    fun reportMoveError(message: String) {
        _lastMoveErrorMessage.value = message
    }

    fun mutate(
        fallbackErrorMessage: String,
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ) {
        scope.launch {
            applyTracked(command, fallbackErrorMessage, onApplied)
        }
    }

    /**
     * Applies a command with mutation-count tracking and error reporting.
     * Returns whether the command was applied. [onFailure] runs whenever the
     * command was not applied, letting callers attach cleanup (e.g. resource
     * deallocation) without re-implementing bookkeeping.
     */
    suspend fun applyTracked(
        command: HomeModelWriter.Command,
        fallbackErrorMessage: String,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {},
        onFailure: suspend () -> Unit = {}
    ): Boolean {
        pendingMutationCount.update { it + 1 }

        try {
            _lastMoveErrorMessage.value = null

            val wasApplied = tryApply(command, fallbackErrorMessage, onApplied)

            if (!wasApplied) {
                if (_lastMoveErrorMessage.value == null) {
                    _lastMoveErrorMessage.value = fallbackErrorMessage
                }
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
        fallbackErrorMessage: String,
        onApplied: suspend (items: List<HomeItem>) -> Unit
    ): Boolean {
        return try {
            apply(command, onApplied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastMoveErrorMessage.value = e.message ?: fallbackErrorMessage
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
