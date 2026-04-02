package com.milki.launcher.presentation.home

import android.os.SystemClock
import android.util.Log
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central write coordinator for home-layout mutations.
 *
 * It owns serialization, loading/error bookkeeping, and persistence commits so
 * call sites stay focused on user intent instead of write plumbing.
 */
internal class HomeMutationCoordinator(
    private val homeRepository: HomeRepository,
    private val modelWriter: HomeModelWriter,
    private val openFolderIdFlow: MutableStateFlow<String?>,
    private val scope: CoroutineScope
) {

    private companion object {
        private const val TAG = "HomeMutationCoordinator"
        private const val SLOW_MUTATION_THRESHOLD_MS = 120L
    }

    private val positionUpdateMutex = Mutex()

    private val _pendingPositionUpdateCount = MutableStateFlow(0)
    val pendingPositionUpdateCount: StateFlow<Int> = _pendingPositionUpdateCount

    private val _lastMoveErrorMessage = MutableStateFlow<String?>(null)
    val lastMoveErrorMessage: StateFlow<String?> = _lastMoveErrorMessage

    fun launchSerializedMutation(
        fallbackErrorMessage: String,
        mutation: suspend () -> Boolean
    ) {
        scope.launch {
            positionUpdateMutex.withLock {
                _pendingPositionUpdateCount.update { current -> current + 1 }
                _lastMoveErrorMessage.value = null

                try {
                    val startedAt = SystemClock.elapsedRealtime()
                    val wasApplied = mutation()
                    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    if (elapsedMs >= SLOW_MUTATION_THRESHOLD_MS) {
                        Log.w(TAG, "Slow home mutation: ${elapsedMs}ms")
                    }

                    if (!wasApplied) {
                        _lastMoveErrorMessage.value = fallbackErrorMessage
                    }
                } catch (exception: Exception) {
                    _lastMoveErrorMessage.value = exception.message ?: fallbackErrorMessage
                } finally {
                    _pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    suspend fun <T> withMutationLock(block: suspend () -> T): T {
        return positionUpdateMutex.withLock {
            block()
        }
    }

    suspend fun applyWriterCommand(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ): Boolean {
        val currentItems = homeRepository.pinnedItems.first()
        return when (
            val result = modelWriter.apply(
                currentItems = currentItems,
                command = command
            )
        ) {
            is HomeModelWriter.Result.Applied -> {
                persistUpdatedItems(currentItems = currentItems, updatedItems = result.items)
                onApplied(result.items)
                true
            }

            is HomeModelWriter.Result.Rejected -> false
        }
    }

    suspend fun persistUpdatedItems(
        currentItems: List<HomeItem>,
        updatedItems: List<HomeItem>
    ) {
        if (updatedItems == currentItems) {
            return
        }

        homeRepository.replacePinnedItems(updatedItems)

        val openFolderId = openFolderIdFlow.value
        if (openFolderId != null && updatedItems.none { it.id == openFolderId }) {
            openFolderIdFlow.value = null
        }
    }

    fun clearMoveError() {
        _lastMoveErrorMessage.value = null
    }
}