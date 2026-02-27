package com.jons.iptv

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jons.iptv.data.CategoryChannels
import com.jons.iptv.data.Channel
import com.jons.iptv.data.ChannelRepository
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.ui.GroupedChannelAdapter
import kotlinx.coroutines.launch
import java.util.LinkedHashMap
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val LOAD_CONTROL_MIN_BUFFER_MS = 5_000
        private const val LOAD_CONTROL_MAX_BUFFER_MS = 15_000
        private const val LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS = 800
        private const val LOAD_CONTROL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500
        private const val TRACK_MAX_VIDEO_SIZE_SD_WIDTH = 1280
        private const val TRACK_MAX_VIDEO_SIZE_SD_HEIGHT = 720
        private const val SWITCH_WINDOW_MS = 20_000L
        private const val MAX_SWITCH_COUNT_IN_WINDOW = 3
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer

    private val repository = ChannelRepository()
    private val groupedChannelAdapter = GroupedChannelAdapter { channel ->
        onChannelSelected(channel)
    }

    private var menuVisible: Boolean = true
    private var pendingMenuRestoreState: Parcelable? = null
    private var pendingMenuFocusPosition: Int? = null

    private var currentChannel: Channel? = null
    private var currentStreamIndex: Int = 0
    private var overlayHideRunnable: Runnable? = null
    private var playbackFailureDialog: AlertDialog? = null
    private var playbackFailureDialogAnimatedDismiss: Boolean = false
    private var isSwitchingStream: Boolean = false
    private var retriedChannelKey: String? = null
    private var retriedStreamIndex: Int = -1
    private var switchWindowChannelKey: String? = null
    private val switchTimestampsMs = ArrayDeque<Long>()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (isFinishing || isDestroyed) return
            val channel = currentChannel ?: return

            val currentUrl = channel.streamUrls.getOrNull(currentStreamIndex)
            val channelKey = buildChannelKey(channel)
            Log.w(
                TAG,
                "Playback error channel=${channel.name}, index=$currentStreamIndex, url=$currentUrl, code=${error.errorCodeName}",
                error
            )

            if (!shouldAutoSwitch(error)) {
                Log.w(TAG, "Skip auto-switch for error code=${error.errorCodeName}")
                return
            }

            if (isTransientNetworkError(error) && shouldRetryCurrentStream(channelKey, currentStreamIndex)) {
                Log.w(
                    TAG,
                    "Retry current stream once channel=${channel.name}, index=$currentStreamIndex, code=${error.errorCodeName}"
                )
                retryCurrentStream(channel, currentStreamIndex)
                return
            }

            if (!canSwitchInShortWindow(channelKey)) {
                Log.w(
                    TAG,
                    "Skip auto-switch due to short-window limit channel=${channel.name}, switchCount=${switchTimestampsMs.size}"
                )
                isSwitchingStream = false
                showPlaybackFailureDialog(channel)
                return
            }

            if (tryPlayFrom(channel, currentStreamIndex + 1)) {
                recordAutoSwitch(channelKey)
                if (!isSwitchingStream) {
                    isSwitchingStream = true
                    Toast.makeText(this@MainActivity, getString(R.string.switching_stream), Toast.LENGTH_SHORT).show()
                }
                return
            }

            isSwitchingStream = false
            showPlaybackFailureDialog(channel)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPlayer()
        initList()
        initMenuInteractions()
        loadChannels()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (menuVisible) {
                        hideMenu(moveFocusToPlayer = true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!menuVisible) {
                        showMenu()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
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

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(
                        TRACK_MAX_VIDEO_SIZE_SD_WIDTH,
                        TRACK_MAX_VIDEO_SIZE_SD_HEIGHT
                    )
                    .setForceHighestSupportedBitrate(false)
            )
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
            .also {
                it.addListener(playerListener)
                binding.playerView.player = it
            }
    }

    private fun initList() {
        binding.groupedChannelRecycler.layoutManager = LinearLayoutManager(this)
        binding.groupedChannelRecycler.adapter = groupedChannelAdapter
    }

    private fun initMenuInteractions() {
        binding.playerContainer.setOnClickListener {
            if (menuVisible) {
                hideMenu(moveFocusToPlayer = true)
            } else {
                showMenu()
            }
        }
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            runCatching { repository.fetchChannels() }
                .onSuccess { channels ->
                    val groupedChannels = buildGroupedChannels(channels)
                    groupedChannelAdapter.submitGroups(groupedChannels)

                    val initialChannel = groupedChannels.firstOrNull()?.channels?.firstOrNull()
                    if (initialChannel != null) {
                        groupedChannelAdapter.setExpandedGroup(initialChannel.category)
                        playChannel(initialChannel, 0)
                    }

                    showMenu()
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun buildGroupedChannels(channels: List<Channel>): List<CategoryChannels> {
        val grouped = LinkedHashMap<String, MutableList<Channel>>()
        channels.forEach { channel ->
            val category = channel.category.ifBlank { getString(R.string.category_other) }
            grouped.getOrPut(category) { mutableListOf() }.add(channel)
        }
        return grouped.map { (category, groupedChannels) ->
            CategoryChannels(category = category, channels = groupedChannels)
        }
    }

    private fun onChannelSelected(channel: Channel) {
        playChannel(channel, 0)
    }

    private fun playChannel(channel: Channel, streamIndex: Int) {
        isSwitchingStream = false
        resetRetryState()
        resetSwitchWindow(channel)
        if (!tryPlayFrom(channel, streamIndex)) {
            showPlaybackFailureDialog(channel)
        }
    }

    private fun tryPlayFrom(channel: Channel, startIndex: Int): Boolean {
        if (channel.streamUrls.isEmpty() || startIndex !in channel.streamUrls.indices) {
            Log.w(
                TAG,
                "Skip playback channel=${channel.name}, urlCount=${channel.streamUrls.size}, startIndex=$startIndex"
            )
            return false
        }

        Log.d(
            TAG,
            "Start playback attempt channel=${channel.name}, urlCount=${channel.streamUrls.size}, startIndex=$startIndex"
        )

        for (index in startIndex until channel.streamUrls.size) {
            val streamUrl = channel.streamUrls[index]
            val played = runCatching {
                currentChannel = channel
                currentStreamIndex = index
                val mediaItem = buildMediaItem(streamUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }.onFailure { throwable ->
                Log.e(
                    TAG,
                    "Playback setup failed channel=${channel.name}, index=$index, url=$streamUrl",
                    throwable
                )
            }.isSuccess

            if (played) {
                Log.d(TAG, "Playback setup success channel=${channel.name}, index=$index, url=$streamUrl")
                playbackFailureDialog?.dismiss()
                groupedChannelAdapter.setPlayingChannel(channel)
                showOverlay(channel)
                return true
            }
        }

        Log.w(TAG, "All playback setup attempts failed channel=${channel.name}, startIndex=$startIndex")
        return false
    }

    private fun showMenu() {
        menuVisible = true
        binding.menuContainer.visibility = View.VISIBLE
        restoreMenuStateOrFocusFirst()
    }

    private fun hideMenu(moveFocusToPlayer: Boolean = true) {
        menuVisible = false
        saveMenuState()
        binding.menuContainer.visibility = View.GONE
        if (moveFocusToPlayer) {
            binding.playerContainer.requestFocus()
        }
    }

    private fun saveMenuState() {
        val recyclerView = binding.groupedChannelRecycler
        pendingMenuRestoreState = recyclerView.layoutManager?.onSaveInstanceState()
        pendingMenuFocusPosition = recyclerView.findFocusedItemPosition()
    }

    private fun restoreMenuStateOrFocusFirst() {
        val recyclerView = binding.groupedChannelRecycler
        val savedState = pendingMenuRestoreState
        val focusPosition = pendingMenuFocusPosition

        if (savedState != null) {
            recyclerView.post {
                recyclerView.layoutManager?.onRestoreInstanceState(savedState)
                if (focusPosition != null) {
                    recyclerView.requestItemFocus(focusPosition)
                } else {
                    recyclerView.requestFocus()
                }
            }
            pendingMenuRestoreState = null
            pendingMenuFocusPosition = null
            return
        }

        if (focusPosition != null && recyclerView.requestItemFocus(focusPosition)) {
            pendingMenuFocusPosition = null
            return
        }

        requestFirstItemFocus(recyclerView)
    }

    private fun RecyclerView.findFocusedItemPosition(): Int? {
        val focused = findFocus() ?: return null
        val holder = findContainingViewHolder(focused) ?: return null
        return holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
    }

    private fun RecyclerView.requestItemFocus(position: Int): Boolean {
        val itemCount = adapter?.itemCount ?: 0
        if (position !in 0 until itemCount) return false

        scrollToPosition(position)
        post {
            val holder = findViewHolderForAdapterPosition(position)
            val focused = holder?.itemView?.requestFocus() == true
            if (!focused) {
                requestFocus()
            }
        }
        return true
    }

    private fun requestFirstItemFocus(recyclerView: RecyclerView) {
        recyclerView.post {
            val itemCount = recyclerView.adapter?.itemCount ?: 0
            if (itemCount == 0) {
                recyclerView.requestFocus()
                return@post
            }
            recyclerView.requestItemFocus(0)
        }
    }

    private fun showPlaybackFailureDialog(channel: Channel) {
        if (isFinishing || isDestroyed) return
        if (playbackFailureDialog?.isShowing == true) return

        playbackFailureDialogAnimatedDismiss = false
        val dialogView = layoutInflater.inflate(R.layout.dialog_playback_failure_cctv, null)
        dialogView.findViewById<TextView>(R.id.failureTitle).text = getString(R.string.playback_failed_title)
        dialogView.findViewById<TextView>(R.id.failureMessage).text =
            getString(R.string.playback_failed_message, channel.name)

        val retryButton = dialogView.findViewById<TextView>(R.id.buttonRetry)
        val closeButton = dialogView.findViewById<TextView>(R.id.buttonClose)

        playbackFailureDialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_IPTV_PlaybackFailureDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    if (playbackFailureDialog === dialog) {
                        playbackFailureDialog = null
                        playbackFailureDialogAnimatedDismiss = false
                    }
                }
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                playDialogEnterAnimation(dialog)

                retryButton.setOnClickListener {
                    dismissPlaybackFailureDialog(dialog) {
                        playChannel(channel, 0)
                    }
                }
                closeButton.setOnClickListener {
                    dismissPlaybackFailureDialog(dialog)
                }
            }
    }

    private fun playDialogEnterAnimation(dialog: AlertDialog) {
        val decorView = dialog.window?.decorView ?: return
        val content = decorView.findViewById<View>(android.R.id.content) ?: decorView
        content.animate().cancel()
        content.alpha = 0f
        content.scaleX = 0.92f
        content.scaleY = 0.92f
        content.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dismissPlaybackFailureDialog(dialog: AlertDialog, onDismissed: (() -> Unit)? = null) {
        if (playbackFailureDialogAnimatedDismiss) return
        val decorView = dialog.window?.decorView
        val content = decorView?.findViewById<View>(android.R.id.content) ?: decorView
        if (content == null) {
            playbackFailureDialogAnimatedDismiss = true
            dialog.dismiss()
            onDismissed?.invoke()
            return
        }

        playbackFailureDialogAnimatedDismiss = true
        content.animate().cancel()
        content.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(220L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
                onDismissed?.invoke()
            }
            .start()
    }

    private fun shouldAutoSwitch(error: PlaybackException): Boolean {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> true

            else -> false
        }
    }

    private fun isTransientNetworkError(error: PlaybackException): Boolean {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true

            else -> false
        }
    }

    private fun retryCurrentStream(channel: Channel, index: Int) {
        val streamUrl = channel.streamUrls.getOrNull(index) ?: return
        val channelKey = buildChannelKey(channel)
        retriedChannelKey = channelKey
        retriedStreamIndex = index

        runCatching {
            val mediaItem = buildMediaItem(streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }.onFailure { throwable ->
            Log.e(
                TAG,
                "Retry setup failed channel=${channel.name}, index=$index, url=$streamUrl",
                throwable
            )
        }
    }

    private fun shouldRetryCurrentStream(channelKey: String, streamIndex: Int): Boolean {
        return retriedChannelKey != channelKey || retriedStreamIndex != streamIndex
    }

    private fun canSwitchInShortWindow(channelKey: String): Boolean {
        val now = System.currentTimeMillis()
        if (switchWindowChannelKey != channelKey) {
            switchWindowChannelKey = channelKey
            switchTimestampsMs.clear()
        }

        while (switchTimestampsMs.isNotEmpty() && now - switchTimestampsMs.first() > SWITCH_WINDOW_MS) {
            switchTimestampsMs.removeFirst()
        }

        return switchTimestampsMs.size < MAX_SWITCH_COUNT_IN_WINDOW
    }

    private fun recordAutoSwitch(channelKey: String) {
        val now = System.currentTimeMillis()
        if (switchWindowChannelKey != channelKey) {
            switchWindowChannelKey = channelKey
            switchTimestampsMs.clear()
        }

        while (switchTimestampsMs.isNotEmpty() && now - switchTimestampsMs.first() > SWITCH_WINDOW_MS) {
            switchTimestampsMs.removeFirst()
        }

        switchTimestampsMs.addLast(now)
        resetRetryState()
    }

    private fun resetRetryState() {
        retriedChannelKey = null
        retriedStreamIndex = -1
    }

    private fun resetSwitchWindow(channel: Channel) {
        switchWindowChannelKey = buildChannelKey(channel)
        switchTimestampsMs.clear()
    }

    private fun buildChannelKey(channel: Channel): String {
        return "${channel.category}|${channel.name}"
    }

    private fun buildMediaItem(url: String): MediaItem {
        val inferredMimeType = inferMimeType(url)
        return MediaItem.Builder()
            .setUri(url)
            .apply {
                if (inferredMimeType != null) {
                    setMimeType(inferredMimeType)
                }
            }
            .build()
    }

    private fun inferMimeType(url: String): String? {
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

    private fun showOverlay(channel: Channel) {
        binding.overlayName.text = channel.name
        binding.overlayLogo.load(channel.logoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_channel_placeholder)
            error(R.drawable.ic_channel_placeholder)
        }

        binding.channelOverlay.animate().cancel()
        if (binding.channelOverlay.visibility != View.VISIBLE) {
            binding.channelOverlay.alpha = 0f
            binding.channelOverlay.translationY = resources.getDimension(R.dimen.overlay_enter_start_translation)
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
            if (!isFinishing && !isDestroyed) {
                binding.channelOverlay.animate().cancel()
                binding.channelOverlay.animate()
                    .alpha(0f)
                    .translationY(resources.getDimension(R.dimen.overlay_exit_end_translation))
                    .setDuration(220L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        binding.channelOverlay.visibility = View.GONE
                    }
                    .start()
            }
        }.also {
            binding.channelOverlay.postDelayed(it, 3000)
        }
    }

    override fun onDestroy() {
        playbackFailureDialog?.dismiss()
        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        binding.channelOverlay.animate().cancel()
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}
