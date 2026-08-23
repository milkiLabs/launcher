package com.milki.launcher.data.icon

import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Hit-rate and slow-load telemetry for [AppIconMemoryCache].
 *
 * Extracted from the cache so the caching logic stays free of accounting
 * concerns. This class is pure observability: thread-safe counters plus
 * threshold-based log warnings. It holds no state the cache depends on,
 * so it can be replaced with a no-op in tests.
 */
class IconCacheMetrics {

    private val requestCount = AtomicLong(0)
    private val hitCount = AtomicLong(0)

    /**
     * Records a cache lookup outcome. Logs the running hit rate every
     * [HIT_RATE_LOG_INTERVAL] requests.
     */
    fun recordRequest(wasHit: Boolean) {
        val totalRequests = requestCount.incrementAndGet()
        if (wasHit) {
            hitCount.incrementAndGet()
        }

        if (totalRequests % HIT_RATE_LOG_INTERVAL != 0L) {
            return
        }

        val currentHits = hitCount.get()
        val hitRatePercent = if (totalRequests == 0L) {
            0.0
        } else {
            (currentHits * PERCENT_SCALE) / totalRequests.toDouble()
        }

        Log.d(
            LOG_TAG,
            String.format(
                Locale.US,
                "Icon cache hit rate %.1f%% (%d/%d)",
                hitRatePercent,
                currentHits,
                totalRequests
            )
        )
    }

    /**
     * Logs a warning when a single package icon load exceeded
     * [SLOW_SINGLE_LOAD_MS].
     */
    fun recordSingleLoadDuration(packageName: String, elapsedMs: Long) {
        if (elapsedMs >= SLOW_SINGLE_LOAD_MS) {
            Log.w(LOG_TAG, "Slow icon load for $packageName: ${elapsedMs}ms")
        }
    }

    /**
     * Logs a warning when a batch preload that actually loaded packages
     * exceeded [SLOW_PRELOAD_BATCH_MS].
     */
    fun recordPreloadBatchDuration(elapsedMs: Long, loadedCount: Int) {
        if (loadedCount > 0 && elapsedMs >= SLOW_PRELOAD_BATCH_MS) {
            Log.w(
                LOG_TAG,
                "Slow icon preload batch: ${elapsedMs}ms for $loadedCount packages"
            )
        }
    }

    private companion object {
        // Kept identical to the cache's historical tag so existing log filters continue to match.
        const val LOG_TAG = "AppIconMemoryCache"

        const val PERCENT_SCALE = 100.0

        const val SLOW_SINGLE_LOAD_MS = 24L
        const val SLOW_PRELOAD_BATCH_MS = 120L
        const val HIT_RATE_LOG_INTERVAL = 200L
    }
}
