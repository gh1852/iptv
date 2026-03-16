package com.jons.iptv.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.jons.iptv.data.Channel

class PlaybackController(
    private val store: PlaybackStore,
    private val playerAdapter: ExoPlayerAdapter,
    private val logger: PlaybackLogger,
    private val callbacks: Callbacks,
    private val bufferingTimeoutMs: Long = DEFAULT_BUFFERING_TIMEOUT_MS,
    private val switchGapMs: Long = DEFAULT_SWITCH_GAP_MS,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    interface Callbacks {
        fun onPrepareToPlay(channel: Channel, streamIndex: Int, streamUrl: String)
        fun onSwitchingSource(channel: Channel, fromIndex: Int, toIndex: Int, reason: String)
        fun onStreamPlaying(channel: Channel, streamIndex: Int)
        fun onAllSourcesFailed(channel: Channel)
    }

    private var bufferingTimeoutRunnable: Runnable? = null
    private var delayedSwitchRunnable: Runnable? = null

    fun play(channel: Channel, startIndex: Int = 0) {
        clearPending()

        if (channel.streamUrls.isEmpty() || startIndex !in channel.streamUrls.indices) {
            logger.w("Invalid play request channel=${channel.name}, startIndex=$startIndex, size=${channel.streamUrls.size}")
            store.markFailed()
            callbacks.onAllSourcesFailed(channel)
            return
        }

        attempt(channel, startIndex, reason = "manual_play")
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                val session = store.session ?: return
                scheduleBufferingTimeout(session)
            }
            Player.STATE_READY -> {
                cancelBufferingTimeout()
                val session = store.session ?: return
                if (session.firstFrameRendered) return
                val updated = store.markPlaying(session.id) ?: return
                logger.i("First frame ready channel=${updated.channel.name}, index=${updated.streamIndex}")
                callbacks.onStreamPlaying(updated.channel, updated.streamIndex)
            }
            Player.STATE_ENDED -> {
                cancelBufferingTimeout()
                val session = store.session ?: return
                logger.w("Stream ended unexpectedly channel=${session.channel.name}, index=${session.streamIndex}")
                switchToNextOrFail(session.channel, session.streamIndex, reason = "stream_ended")
            }
            else -> cancelBufferingTimeout()
        }
    }

    fun onPlayerError(error: PlaybackException) {
        val session = store.session ?: return
        logger.w(
            "Player error channel=${session.channel.name}, index=${session.streamIndex}, code=${error.errorCodeName}"
        )
        switchToNextOrFail(session.channel, session.streamIndex, reason = "player_error_${error.errorCodeName}")
    }

    fun release() {
        clearPending()
        store.clear()
    }

    private fun attempt(channel: Channel, streamIndex: Int, reason: String) {
        if (streamIndex !in channel.streamUrls.indices) {
            store.markFailed()
            callbacks.onAllSourcesFailed(channel)
            return
        }

        val url = channel.streamUrls[streamIndex]
        val session = store.createSession(channel, streamIndex)
        logger.i(
            "Attempt playback channel=${channel.name}, index=$streamIndex, reason=$reason"
        )

        callbacks.onPrepareToPlay(channel, streamIndex, url)

        runCatching {
            playerAdapter.play(url)
        }.onFailure { throwable ->
            logger.e(
                "Player setup failed channel=${channel.name}, index=$streamIndex",
                throwable
            )
            switchToNextOrFail(channel, streamIndex, reason = "setup_exception")
        }
    }

    private fun switchToNextOrFail(channel: Channel, currentIndex: Int, reason: String) {
        cancelBufferingTimeout()

        val nextIndex = currentIndex + 1
        if (nextIndex > channel.streamUrls.lastIndex) {
            logger.w("All sources failed channel=${channel.name}, lastIndex=$currentIndex")
            store.markFailed()
            callbacks.onAllSourcesFailed(channel)
            return
        }

        logger.w(
            "Switch source channel=${channel.name}, from=$currentIndex, to=$nextIndex, reason=$reason"
        )
        callbacks.onSwitchingSource(channel, currentIndex, nextIndex, reason)

        delayedSwitchRunnable?.let { handler.removeCallbacks(it) }
        delayedSwitchRunnable = Runnable {
            delayedSwitchRunnable = null
            attempt(channel, nextIndex, reason = "auto_switch")
        }.also {
            handler.postDelayed(it, switchGapMs)
        }
    }

    private fun scheduleBufferingTimeout(session: PlaybackSession) {
        cancelBufferingTimeout()
        bufferingTimeoutRunnable = Runnable {
            val current = store.session
            if (current == null || current.id != session.id) return@Runnable
            logger.w(
                "Buffering timeout channel=${session.channel.name}, index=${session.streamIndex}, timeoutMs=$bufferingTimeoutMs"
            )
            switchToNextOrFail(session.channel, session.streamIndex, reason = "buffering_timeout")
        }.also {
            handler.postDelayed(it, bufferingTimeoutMs)
        }
    }

    private fun cancelBufferingTimeout() {
        bufferingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        bufferingTimeoutRunnable = null
    }

    private fun clearPending() {
        cancelBufferingTimeout()
        delayedSwitchRunnable?.let { handler.removeCallbacks(it) }
        delayedSwitchRunnable = null
    }

    companion object {
        private const val DEFAULT_BUFFERING_TIMEOUT_MS = 15_000L
        private const val DEFAULT_SWITCH_GAP_MS = 500L
    }
}
