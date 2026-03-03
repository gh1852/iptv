package com.jons.iptv.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

class MediaItemFactory(
    private val logTag: String
) {
    fun buildMediaItem(url: String): MediaItem {
        val inferredMimeType = inferMimeType(url)
        Log.d(logTag, "Build media item url=$url, inferredMime=$inferredMimeType")
        return MediaItem.Builder()
            .setUri(url)
            .apply {
                if (inferredMimeType != null) {
                    setMimeType(inferredMimeType)
                }
            }
            .build()
    }

    fun inferMimeType(url: String): String? {
        val parsed = Uri.parse(url)
        val normalizedUrl = url.lowercase()
        val path = parsed.path?.lowercase().orEmpty()

        return when {
            path.endsWith(".m3u8") || normalizedUrl.contains(".m3u8?") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") || normalizedUrl.contains(".mpd?") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".ism") || path.endsWith(".isml") || normalizedUrl.contains("format=ism") -> MimeTypes.APPLICATION_SS
            path.endsWith(".mp4") || normalizedUrl.contains(".mp4?") -> MimeTypes.VIDEO_MP4
            path.endsWith(".ts") || normalizedUrl.contains(".ts?") -> MimeTypes.VIDEO_MP2T
            else -> null
        }
    }
}
