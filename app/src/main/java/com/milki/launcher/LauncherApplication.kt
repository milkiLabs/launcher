package com.milki.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class LauncherApplication : Application(), ImageLoaderFactory {
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Use 15% of available memory
                    .build()
            }
            .diskCache(null) // Disable disk cache - unnecessary for launcher icons
            .allowHardware(false) // Disable hardware bitmaps for launcher safety
            .respectCacheHeaders(false)
            .build()
    }
}
