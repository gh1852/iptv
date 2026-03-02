package com.jons.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import okhttp3.OkHttpClient

class IPTVApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val imageHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val cacheControl = response.header("Cache-Control").orEmpty()
                if (
                    cacheControl.contains("no-store", ignoreCase = true) ||
                    cacheControl.contains("no-cache", ignoreCase = true) ||
                    cacheControl.contains("max-age", ignoreCase = true)
                ) {
                    response
                } else {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=604800")
                        .build()
                }
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageHttpClient)
            .respectCacheHeaders(false)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("logo_image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
