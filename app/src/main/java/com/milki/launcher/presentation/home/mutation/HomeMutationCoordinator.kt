package com.milki.launcher.presentation.home

import android.os.SystemClock
import android.util.Log
import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

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
    private val scope: CoroutineScope,
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private companion object {
        private const val TAG = "HomeMutationCoordinator"
        private const val SLOW_MUTATION_THRESHOLD_MS = 120L
    }

    private sealed interface QueuedMutation {
        data class Immediate(val payload: MutationPayload) : QueuedMutation
        data class CoalescedKey(val key: String) : QueuedMutation
    }

    private data class MutationPayload(
        val fallbackErrorMessage: String,
        val mutation: suspend () -> Boolean
    )

    private val mutationQueue = Channel<QueuedMutation>(capacity = Channel.UNLIMITED)
    private val coalescedMutations = linkedMapOf<String, MutationPayload>()
    private val coalescingLock = Any()

    private val _pendingPositionUpdateCount = MutableStateFlow(0)
    val pendingPositionUpdateCount: StateFlow<Int> = _pendingPositionUpdateCount

    private val _lastMoveErrorMessage = MutableStateFlow<String?>(null)
    val lastMoveErrorMessage: StateFlow<String?> = _lastMoveErrorMessage

    init {
        scope.launch(Dispatchers.Default) {
            processMutationQueue()
        }
    }

    fun launchSerializedMutation(
        fallbackErrorMessage: String,
        coalescingKey: String? = null,
        mutation: suspend () -> Boolean
    ) {
        val payload = MutationPayload(
            fallbackErrorMessage = fallbackErrorMessage,
            mutation = mutation
        )

        val queueResult = if (coalescingKey == null) {
            _pendingPositionUpdateCount.update { current -> current + 1 }
            mutationQueue.trySend(QueuedMutation.Immediate(payload))
        } else {
            synchronized(coalescingLock) {
                val alreadyQueued = coalescedMutations.containsKey(coalescingKey)
                coalescedMutations[coalescingKey] = payload

                if (alreadyQueued) {
                    null
                } else {
                    _pendingPositionUpdateCount.update { current -> current + 1 }
                    mutationQueue.trySend(QueuedMutation.CoalescedKey(coalescingKey))
                }
            }
        }

        if (queueResult != null && queueResult.isFailure) {
            _pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
            _lastMoveErrorMessage.value = fallbackErrorMessage
            Log.e(TAG, "Failed to enqueue mutation", queueResult.exceptionOrNull())
        }
    }

    suspend fun <T> withMutationLock(block: suspend () -> T): T {
        return block()
    }

    private suspend fun processMutationQueue() {
        for (queued in mutationQueue) {
            val payload = when (queued) {
                is QueuedMutation.Immediate -> queued.payload
                is QueuedMutation.CoalescedKey -> {
                    synchronized(coalescingLock) {
                        coalescedMutations.remove(queued.key)
                    }
                }
            }

            if (payload == null) {
                _pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
                continue
            }

            runQueuedMutation(payload)
        }
    }

    private suspend fun runQueuedMutation(payload: MutationPayload) {
        _lastMoveErrorMessage.value = null

        try {
            val startedAt = elapsedRealtimeMs()
            val wasApplied = payload.mutation()
            val elapsedMs = elapsedRealtimeMs() - startedAt
            if (elapsedMs >= SLOW_MUTATION_THRESHOLD_MS) {
                Log.w(TAG, "Slow home mutation: ${elapsedMs}ms")
            }

            if (!wasApplied) {
                _lastMoveErrorMessage.value = payload.fallbackErrorMessage
            }
        } catch (exception: Exception) {
            _lastMoveErrorMessage.value = exception.message ?: payload.fallbackErrorMessage
        } finally {
            _pendingPositionUpdateCount.update { current -> (current - 1).coerceAtLeast(0) }
        }
    }

    suspend fun applyWriterCommand(
        command: HomeModelWriter.Command,
        onApplied: suspend (items: List<HomeItem>) -> Unit = {}
    ): Boolean {
        val currentItems = homeRepository.readPinnedItems()
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
