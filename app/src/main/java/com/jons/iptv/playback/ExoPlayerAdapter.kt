package com.jons.iptv.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerAdapter(
    private val player: ExoPlayer,
    private val mediaItemFactory: (String) -> MediaItem
) {
    fun play(url: String) {
        player.setMediaItem(mediaItemFactory(url))
        player.prepare()
        player.playWhenReady = true
    }
}
