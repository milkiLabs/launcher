package com.milki.launcher.presentation.home

import com.milki.launcher.domain.homegraph.HomeModelWriter
import com.milki.launcher.domain.model.GridPosition
import com.milki.launcher.domain.model.HomeItem
import com.milki.launcher.domain.repository.HomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HomeMutationCoordinatorTest {

    @Test
    fun coalescingKey_keepsOnlyLatestQueuedMutation() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val coordinator = HomeMutationCoordinator(
                homeRepository = FakeHomeRepository(),
                modelWriter = HomeModelWriter(),
                openFolderIdFlow = MutableStateFlow(null),
                scope = scope,
                elapsedRealtimeMs = { System.currentTimeMillis() }
            )

            val keyedRunCount = AtomicInteger(0)
            val keyedLastValue = AtomicInteger(0)
            val slowStarted = CountDownLatch(1)
            val releaseSlow = CountDownLatch(1)

            coordinator.launchSerializedMutation(
                fallbackErrorMessage = "slow",
                coalescingKey = null
            ) {
                slowStarted.countDown()
                releaseSlow.await(2, TimeUnit.SECONDS)
                true
            }

            assertTrue("Slow mutation did not start", slowStarted.await(1, TimeUnit.SECONDS))

            coordinator.launchSerializedMutation(
                fallbackErrorMessage = "k1",
                coalescingKey = "move:item-1"
            ) {
                keyedRunCount.incrementAndGet()
                keyedLastValue.set(1)
                true
            }

            coordinator.launchSerializedMutation(
                fallbackErrorMessage = "k2",
                coalescingKey = "move:item-1"
            ) {
                keyedRunCount.incrementAndGet()
                keyedLastValue.set(2)
                true
            }

            releaseSlow.countDown()

            waitUntilIdle(coordinator)

            assertEquals("Only one coalesced mutation should run", 1, keyedRunCount.get())
            assertEquals("Latest queued mutation should win", 2, keyedLastValue.get())
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }

    @Test
    fun differentCoalescingKeys_areProcessedIndependently() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val coordinator = HomeMutationCoordinator(
                homeRepository = FakeHomeRepository(),
                modelWriter = HomeModelWriter(),
                openFolderIdFlow = MutableStateFlow(null),
                scope = scope,
                elapsedRealtimeMs = { System.currentTimeMillis() }
            )

            val runCount = AtomicInteger(0)

            coordinator.launchSerializedMutation(
                fallbackErrorMessage = "a",
                coalescingKey = "move:item-a"
            ) {
                runCount.incrementAndGet()
                true
            }

            coordinator.launchSerializedMutation(
                fallbackErrorMessage = "b",
                coalescingKey = "move:item-b"
            ) {
                runCount.incrementAndGet()
                true
            }

            waitUntilIdle(coordinator)

            assertEquals(2, runCount.get())
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }

    private fun waitUntilIdle(coordinator: HomeMutationCoordinator) {
        val timeoutMs = 3_000L
        val start = System.currentTimeMillis()
        while (coordinator.pendingPositionUpdateCount.value > 0) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                break
            }
            Thread.sleep(10)
        }
        assertTrue(
            "Mutation queue did not drain within timeout",
            coordinator.pendingPositionUpdateCount.value == 0
        )
    }

    private class FakeHomeRepository : HomeRepository {
        private val state = MutableStateFlow<List<HomeItem>>(emptyList())

        override val pinnedItems: Flow<List<HomeItem>> = state

        override suspend fun readPinnedItems(): List<HomeItem> {
            return state.value
        }

        override suspend fun replacePinnedItems(items: List<HomeItem>) {
            state.value = items
        }

        override suspend fun isPinned(id: String): Boolean {
            return state.value.any { it.id == id }
        }

        override suspend fun findAvailablePosition(columns: Int, maxRows: Int): GridPosition {
            return GridPosition.DEFAULT
        }

        override suspend fun clearAll() {
            state.value = emptyList()
        }
    }
}
