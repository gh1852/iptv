package com.jons.iptv.playback

import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import coil.load
import com.jons.iptv.R
import com.jons.iptv.data.Channel
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.ui.GroupedChannelAdapter

class PlayerEngineCoordinator(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val groupedChannelAdapter: GroupedChannelAdapter,
    private val onShowPlaybackFailureDialog: (Channel) -> Unit,
    private val onDismissPlaybackFailureDialog: () -> Unit,
    private val logTag: String
) {
    companion object {
        private const val LOAD_CONTROL_MIN_BUFFER_MS = 5_000
        private const val LOAD_CONTROL_MAX_BUFFER_MS = 15_000
        private const val LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS = 800
        private const val LOAD_CONTROL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500
        private const val HTTP_CONNECT_TIMEOUT_MS = 4_000
        private const val HTTP_READ_TIMEOUT_MS = 8_000
        private const val OVERLAY_AUTO_HIDE_DELAY_MS = 3_000L
    }

    private lateinit var player: ExoPlayer

    private var currentChannel: Channel? = null
    private var previousChannel: Channel? = null
    private var nextChannel: Channel? = null
    private var currentStreamIndex: Int = 0
    private var overlayHideRunnable: Runnable? = null
    private var isSwitchingStream: Boolean = false
    private var decoderRecoveryRequired: Boolean = false
    private var resumePlaybackOnForeground: Boolean = false
    private var isPlayerInitialized: Boolean = false

    private val playbackStore = PlaybackStore()
    private lateinit var playbackController: PlaybackController

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(
                logTag,
                "Playback state changed state=$playbackState, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, channel=${currentChannel?.name}, index=$currentStreamIndex, position=${player.currentPosition}"
            )
            playbackController.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_READY -> applyKeepScreenOnState(player.playWhenReady)
                Player.STATE_ENDED, Player.STATE_IDLE -> clearKeepScreenOnFlag()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(
                logTag,
                "isPlaying changed isPlaying=$isPlaying, state=${player.playbackState}, channel=${currentChannel?.name}, index=$currentStreamIndex, position=${player.currentPosition}"
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            if (activity.isFinishing || activity.isDestroyed) return
            if (isDecoderFailure(error)) {
                decoderRecoveryRequired = true
            }
            playbackController.onPlayerError(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audioGroups.isEmpty()) {
                Log.w(logTag, "Audio track info: no audio groups, channel=${currentChannel?.name}, index=$currentStreamIndex")
                return
            }

            audioGroups.forEachIndexed { groupIndex, group ->
                group.mediaTrackGroup.length.let { trackCount ->
                    for (trackIndex in 0 until trackCount) {
                        val format = group.mediaTrackGroup.getFormat(trackIndex)
                        val selected = group.isTrackSelected(trackIndex)
                        val supported = group.isTrackSupported(trackIndex)
                        Log.i(
                            logTag,
                            "Audio track info: channel=${currentChannel?.name}, index=$currentStreamIndex, group=$groupIndex, track=$trackIndex, selected=$selected, supported=$supported, mime=${format.sampleMimeType}, codecs=${format.codecs}, language=${format.language}, channels=${format.channelCount}, sampleRate=${format.sampleRate}, label=${format.label}"
                        )
                    }
                }
            }
        }
    }

    fun initPlayerEngine() {
        initPlayer()
        initPlaybackController()
        isPlayerInitialized = true
    }

    fun getCurrentChannel(): Channel? = currentChannel

    fun playChannel(channel: Channel, streamIndex: Int) {
        ensureHealthyPlayerIfNeeded("play_channel")
        Log.d(
            logTag,
            "Play channel request channel=${channel.name}, category=${channel.category}, streamIndex=$streamIndex, streamCount=${channel.streamUrls.size}"
        )
        isSwitchingStream = false
        playbackController.play(channel, streamIndex)
    }

    fun playNextChannel() {
        nextChannel?.let { playChannel(it, 0) }
    }

    fun playPreviousChannel() {
        previousChannel?.let { playChannel(it, 0) }
    }

    fun onStart() {
        if (!isPlayerInitialized) {
            initPlayerEngine()
            currentChannel?.let { channel ->
                playbackController.play(channel, currentStreamIndex)
                if (!resumePlaybackOnForeground) {
                    player.playWhenReady = false
                }
            }
        } else if (resumePlaybackOnForeground) {
            player.playWhenReady = true
        }
        applyKeepScreenOnState(player.playWhenReady)
    }

    fun onPause() {
        if (!isPlayerInitialized) {
            return
        }
        resumePlaybackOnForeground = player.playWhenReady
        clearKeepScreenOnFlag()
        player.playWhenReady = false
    }

    fun onStop() {
        if (!isPlayerInitialized) {
            return
        }
        release()
    }

    fun release() {
        if (!isPlayerInitialized) {
            return
        }
        clearKeepScreenOnFlag()
        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        binding.channelOverlay.animate().cancel()
        playbackController.release()
        player.removeListener(playerListener)
        player.release()
        isPlayerInitialized = false
    }

    private fun initPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                LOAD_CONTROL_MIN_BUFFER_MS,
                LOAD_CONTROL_MAX_BUFFER_MS,
                LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS,
                LOAD_CONTROL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(activity)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setKeepPostFor302Redirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(activity, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLiveTargetOffsetMs(3_000)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))

        player = ExoPlayer.Builder(activity, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also {
                it.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                it.addListener(playerListener)
                binding.playerView.player = it
            }
    }

    private fun initPlaybackController() {
        val logger = PlaybackLogger(logTag)
        val adapter = ExoPlayerAdapter(player) { url ->
            Log.d(logTag, "Build media item url=$url")
            MediaItem.Builder()
                .setUri(url)
                .apply {
                    when {
                        url.contains("miguVIP.php") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .build()
        }
        playbackController = PlaybackController(
            store = playbackStore,
            playerAdapter = adapter,
            logger = logger,
            callbacks = object : PlaybackController.Callbacks {
                override fun onPrepareToPlay(channel: Channel, streamIndex: Int, streamUrl: String) {
                    currentChannel = channel
                    currentStreamIndex = streamIndex
                    applyKeepScreenOnState(true)
                }

                override fun onSwitchingSource(
                    channel: Channel,
                    fromIndex: Int,
                    toIndex: Int,
                    reason: String
                ) {
                    if (!isSwitchingStream) {
                        isSwitchingStream = true
                        Toast.makeText(activity, activity.getString(R.string.switching_stream), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStreamPlaying(channel: Channel, streamIndex: Int) {
                    isSwitchingStream = false
                    onDismissPlaybackFailureDialog()
                    groupedChannelAdapter.setPlayingChannel(channel)
                    refreshAdjacentChannels()
                    showOverlay(channel)
                }

                override fun onAllSourcesFailed(channel: Channel) {
                    isSwitchingStream = false
                    onShowPlaybackFailureDialog(channel)
                }
            }
        )
    }

    private fun refreshAdjacentChannels() {
        val (previous, next) = groupedChannelAdapter.getAdjacentChannels(currentChannel)
        previousChannel = previous
        nextChannel = next
    }

    private fun ensureHealthyPlayerIfNeeded(reason: String) {
        if (!decoderRecoveryRequired) {
            return
        }

        Log.w(logTag, "Recreate player for decoder recovery reason=$reason")
        runCatching {
            player.removeListener(playerListener)
            player.release()
            initPlayer()
            initPlaybackController()
            decoderRecoveryRequired = false
        }.onFailure { throwable ->
            Log.e(logTag, "Player recreation failed reason=$reason", throwable)
            decoderRecoveryRequired = false
        }
    }

    private fun isDecoderFailure(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
    }

    private fun applyKeepScreenOnState(keepScreenOn: Boolean) {
        if (keepScreenOn) {
            binding.playerView.keepScreenOn = true
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }
        clearKeepScreenOnFlag()
    }

    private fun clearKeepScreenOnFlag() {
        binding.playerView.keepScreenOn = false
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showOverlay(channel: Channel) {
        binding.overlayName.text = channel.name
        binding.overlayLogo.load(channel.logoUrl) {
            memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            diskCachePolicy(coil.request.CachePolicy.ENABLED)
            networkCachePolicy(coil.request.CachePolicy.ENABLED)
            crossfade(true)
            placeholder(R.drawable.ic_channel_placeholder)
            error(R.drawable.ic_channel_placeholder)
        }

        binding.channelOverlay.animate().cancel()
        if (binding.channelOverlay.visibility != View.VISIBLE) {
            binding.channelOverlay.alpha = 0f
            binding.channelOverlay.translationY = activity.resources.getDimension(R.dimen.overlay_enter_start_translation)
            binding.channelOverlay.visibility = View.VISIBLE
        }
        binding.channelOverlay.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        overlayHideRunnable = Runnable {
            if (!activity.isFinishing && !activity.isDestroyed) {
                binding.channelOverlay.animate().cancel()
                binding.channelOverlay.animate()
                    .alpha(0f)
                    .translationY(activity.resources.getDimension(R.dimen.overlay_exit_end_translation))
                    .setDuration(220L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        binding.channelOverlay.visibility = View.GONE
                    }
                    .start()
            }
        }.also {
            binding.channelOverlay.postDelayed(it, OVERLAY_AUTO_HIDE_DELAY_MS)
        }
    }
}
