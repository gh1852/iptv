package com.jons.iptv.playback

import com.jons.iptv.data.Channel

enum class PlaybackStatus {
    IDLE,
    CONNECTING,
    PLAYING,
    FAILED
}

data class PlaybackSession(
    val id: Long,
    val channel: Channel,
    val streamIndex: Int,
    val startedAtMs: Long,
    val firstFrameRendered: Boolean = false
)

class PlaybackStore {
    var status: PlaybackStatus = PlaybackStatus.IDLE
        private set

    var session: PlaybackSession? = null
        private set

    private var nextSessionId: Long = 1L

    fun createSession(channel: Channel, streamIndex: Int): PlaybackSession {
        val created = PlaybackSession(
            id = nextSessionId++,
            channel = channel,
            streamIndex = streamIndex,
            startedAtMs = System.currentTimeMillis()
        )
        session = created
        status = PlaybackStatus.CONNECTING
        return created
    }

    fun markPlaying(sessionId: Long): PlaybackSession? {
        val current = session ?: return null
        if (current.id != sessionId) return null

        val updated = current.copy(firstFrameRendered = true)
        session = updated
        status = PlaybackStatus.PLAYING
        return updated
    }

    fun markFailed() {
        status = PlaybackStatus.FAILED
    }

    fun clear() {
        session = null
        status = PlaybackStatus.IDLE
    }
}
