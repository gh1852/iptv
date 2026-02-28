package com.jons.iptv

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
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
import com.jons.iptv.data.AppUpdateRepository
import com.jons.iptv.data.CategoryChannels
import com.jons.iptv.data.Channel
import com.jons.iptv.data.ChannelRepository
import com.jons.iptv.data.UpdateInfo
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.ui.GroupedChannelAdapter
import kotlinx.coroutines.launch
import java.io.File
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
        private const val BACK_PRESS_EXIT_WINDOW_MS = 2_000L
        private const val STARTUP_MENU_AUTO_HIDE_DELAY_MS = 1_500L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer

    private val repository = ChannelRepository()
    private val appUpdateRepository = AppUpdateRepository()
    private val groupedChannelAdapter = GroupedChannelAdapter { channel ->
        onChannelSelected(channel)
    }

    private var menuVisible: Boolean = true
    private var pendingMenuRestoreState: Parcelable? = null
    private var pendingMenuFocusPosition: Int? = null

    private var currentChannel: Channel? = null
    private var previousChannel: Channel? = null
    private var nextChannel: Channel? = null
    private var currentStreamIndex: Int = 0
    private var overlayHideRunnable: Runnable? = null
    private var playbackFailureDialog: Dialog? = null
    private var playbackFailureDialogAnimatedDismiss: Boolean = false
    private var updateDialog: Dialog? = null
    private var updateDialogAnimatedDismiss: Boolean = false
    private var isSwitchingStream: Boolean = false
    private var retriedChannelKey: String? = null
    private var retriedStreamIndex: Int = -1
    private var forcedHlsRetryChannelKey: String? = null
    private var forcedHlsRetryStreamIndex: Int = -1
    private var switchWindowChannelKey: String? = null
    private val switchTimestampsMs = ArrayDeque<Long>()
    private var lastBackPressedAtMs: Long = 0L

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

            if (shouldRetryAsForcedHls(error, channelKey, currentStreamIndex)) {
                Log.w(
                    TAG,
                    "Retry current stream with forced HLS channel=${channel.name}, index=$currentStreamIndex"
                )
                retryCurrentStream(channel, currentStreamIndex, forceHls = true)
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

        enableFullscreenIfPhone()
        initPlayer()
        initList()
        initMenuInteractions()
        loadChannels()
        checkUpdateSilently()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullscreenIfPhone()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (menuVisible) {
                        hideMenu(moveFocusToPlayer = true)
                        return true
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastBackPressedAtMs <= BACK_PRESS_EXIT_WINDOW_MS) {
                        finish()
                    } else {
                        lastBackPressedAtMs = now
                        Toast.makeText(
                            this,
                            getString(R.string.press_back_again_to_exit),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }

                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    nextChannel?.let { playChannel(it, 0) }
                    return true
                }

                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    previousChannel?.let { playChannel(it, 0) }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    return handleMenuConfirmKey()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleMenuConfirmKey(): Boolean {
        if (!menuVisible) {
            showMenu()
            return true
        }

        val focusedView = currentFocus
        if (focusedView != null && isDescendantOf(focusedView, binding.menuContainer)) {
            return false
        }

        hideMenu(moveFocusToPlayer = true)
        return true
    }

    private fun toggleMenuVisibility(moveFocusToPlayerWhenHide: Boolean) {
        if (menuVisible) {
            hideMenu(moveFocusToPlayer = moveFocusToPlayerWhenHide)
        } else {
            showMenu()
        }
    }

    private fun isDescendantOf(view: View, parent: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === parent) return true
            val next = current.parent
            current = if (next is View) next else null
        }
        return false
    }

    private fun enableFullscreenIfPhone() {
        if (isTvDevice()) return

        val appWindow = window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appWindow.setDecorFitsSystemWindows(false)
            appWindow.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            return
        }

        @Suppress("DEPRECATION")
        appWindow.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
    }

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
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
            toggleMenuVisibility(moveFocusToPlayerWhenHide = true)
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
                        showMenu()
                        binding.menuContainer.postDelayed({
                            if (menuVisible && !isFinishing && !isDestroyed) {
                                hideMenu(moveFocusToPlayer = true)
                            }
                        }, STARTUP_MENU_AUTO_HIDE_DELAY_MS)
                    } else {
                        showMenu()
                    }
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
        hideMenu(moveFocusToPlayer = true)
    }

    private fun refreshAdjacentChannels() {
        val (previous, next) = groupedChannelAdapter.getAdjacentChannels(currentChannel)
        previousChannel = previous
        nextChannel = next
    }

    private fun playChannel(channel: Channel, streamIndex: Int) {
        isSwitchingStream = false
        resetRetryState()
        resetForcedHlsRetryState()
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
                resetRetryState()
                resetForcedHlsRetryState()
                playbackFailureDialog?.dismiss()
                groupedChannelAdapter.setPlayingChannel(channel)
                refreshAdjacentChannels()
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
        pendingMenuRestoreState?.let { savedState ->
            recyclerView.layoutManager?.onRestoreInstanceState(savedState)
            pendingMenuRestoreState = null
        }

        val focusPosition = pendingMenuFocusPosition
        pendingMenuFocusPosition = null

        if (focusPosition != null && recyclerView.requestItemFocus(focusPosition)) {
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
        val holder = findViewHolderForAdapterPosition(position)
        if (holder?.itemView?.requestFocus() == true) {
            return true
        }

        post {
            val postedHolder = findViewHolderForAdapterPosition(position)
            if (postedHolder?.itemView?.requestFocus() != true) {
                requestFocus()
            }
        }
        return true
    }

    private fun requestFirstItemFocus(recyclerView: RecyclerView) {
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount == 0) {
            recyclerView.requestFocus()
            return
        }
        recyclerView.requestItemFocus(0)
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

        playbackFailureDialog = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(true)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setOnDismissListener {
                if (playbackFailureDialog === this) {
                    playbackFailureDialog = null
                    playbackFailureDialogAnimatedDismiss = false
                }
            }
            show()
            playDialogEnterAnimation(this)

            retryButton.setOnClickListener {
                dismissPlaybackFailureDialog(this) {
                    playChannel(channel, 0)
                }
            }
            closeButton.setOnClickListener {
                dismissPlaybackFailureDialog(this)
            }
        }
    }

    private fun playDialogEnterAnimation(dialog: Dialog) {
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

    private fun dismissPlaybackFailureDialog(dialog: Dialog, onDismissed: (() -> Unit)? = null) {
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

    private fun retryCurrentStream(channel: Channel, index: Int, forceHls: Boolean = false) {
        val streamUrl = channel.streamUrls.getOrNull(index) ?: return
        val channelKey = buildChannelKey(channel)
        retriedChannelKey = channelKey
        retriedStreamIndex = index
        if (forceHls) {
            forcedHlsRetryChannelKey = channelKey
            forcedHlsRetryStreamIndex = index
        }

        runCatching {
            val mediaItem = buildMediaItem(streamUrl, forceHls = forceHls)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }.onFailure { throwable ->
            Log.e(
                TAG,
                "Retry setup failed channel=${channel.name}, index=$index, url=$streamUrl, forceHls=$forceHls",
                throwable
            )
        }
    }

    private fun shouldRetryCurrentStream(channelKey: String, streamIndex: Int): Boolean {
        return retriedChannelKey != channelKey || retriedStreamIndex != streamIndex
    }

    private fun shouldRetryAsForcedHls(error: PlaybackException, channelKey: String, streamIndex: Int): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
            return false
        }

        return forcedHlsRetryChannelKey != channelKey || forcedHlsRetryStreamIndex != streamIndex
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
        resetForcedHlsRetryState()
    }

    private fun resetRetryState() {
        retriedChannelKey = null
        retriedStreamIndex = -1
    }

    private fun resetForcedHlsRetryState() {
        forcedHlsRetryChannelKey = null
        forcedHlsRetryStreamIndex = -1
    }

    private fun resetSwitchWindow(channel: Channel) {
        switchWindowChannelKey = buildChannelKey(channel)
        switchTimestampsMs.clear()
    }

    private fun buildChannelKey(channel: Channel): String {
        return "${channel.category}|${channel.name}"
    }

    private fun buildMediaItem(url: String, forceHls: Boolean = false): MediaItem {
        val inferredMimeType = if (forceHls) MimeTypes.APPLICATION_M3U8 else inferMimeType(url)
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

    private fun checkUpdateSilently() {
        lifecycleScope.launch {
            runCatching { appUpdateRepository.fetchLatest() }
                .onSuccess { latest ->
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
                    val hasNew = appUpdateRepository.hasNewVersion(
                        latestVersionCode = latest.versionCode,
                        currentVersionCode = currentVersionCode
                    )
                    val forceUpdate = latest.force || currentVersionCode < latest.minSupportedVersionCode
                    if (hasNew || forceUpdate) {
                        showUpdateDialog(latest, forceUpdate)
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "Update check failed", throwable)
                }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo, forceUpdate: Boolean) {
        if (isFinishing || isDestroyed) return
        if (updateDialog?.isShowing == true) return

        val message = if (!updateInfo.changelog.isNullOrBlank()) {
            getString(
                R.string.update_dialog_message_with_changelog,
                updateInfo.versionName,
                updateInfo.changelog
            )
        } else {
            getString(R.string.update_dialog_message_no_changelog, updateInfo.versionName)
        }

        updateDialogAnimatedDismiss = false
        val dialogView = layoutInflater.inflate(R.layout.dialog_playback_failure_cctv, null)
        dialogView.findViewById<TextView>(R.id.failureTitle).text = getString(R.string.update_dialog_title)
        dialogView.findViewById<TextView>(R.id.failureMessage).text = message

        val nowButton = dialogView.findViewById<TextView>(R.id.buttonRetry)
        val laterButton = dialogView.findViewById<TextView>(R.id.buttonClose)
        nowButton.text = getString(R.string.update_action_now)
        laterButton.text = getString(R.string.update_action_later)
        laterButton.visibility = if (forceUpdate) View.GONE else View.VISIBLE

        updateDialog = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(!forceUpdate)
            setCanceledOnTouchOutside(!forceUpdate)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setOnDismissListener {
                if (updateDialog === this) {
                    updateDialog = null
                    updateDialogAnimatedDismiss = false
                }
            }
            show()
            window?.setLayout(
                resources.getDimensionPixelSize(R.dimen.update_dialog_fixed_width),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            playDialogEnterAnimation(this)

            if (forceUpdate) {
                nowButton.requestFocus()
            }

            nowButton.setOnClickListener {
                dismissUpdateDialog(this) {
                    downloadAndInstallUpdate(updateInfo)
                }
            }
            laterButton.setOnClickListener {
                dismissUpdateDialog(this)
            }
        }
    }


    private fun dismissUpdateDialog(dialog: Dialog, onDismissed: (() -> Unit)? = null) {
        if (updateDialogAnimatedDismiss) return
        val decorView = dialog.window?.decorView
        val content = decorView?.findViewById<View>(android.R.id.content) ?: decorView
        if (content == null) {
            updateDialogAnimatedDismiss = true
            dialog.dismiss()
            onDismissed?.invoke()
            return
        }

        updateDialogAnimatedDismiss = true
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


    private fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()

            val updateDir = File(cacheDir, "updates")
            val targetFile = File(updateDir, "iptv-update-${updateInfo.versionCode}.apk")

            runCatching {
                val downloaded = appUpdateRepository.downloadApk(updateInfo.apkUrl, targetFile)
                appUpdateRepository.verifySha256(downloaded, updateInfo.sha256)
                downloaded
            }.onSuccess { apkFile ->
                if (!canRequestPackageInstallsCompat()) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.update_install_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
                    openUnknownSourcesSettings()
                    return@onSuccess
                }
                installDownloadedApk(apkFile)
            }.onFailure { throwable ->
                Log.w(TAG, "Update install flow failed", throwable)
                val messageRes = if (throwable.message?.contains("SHA256", ignoreCase = true) == true) {
                    R.string.update_verification_failed
                } else {
                    R.string.update_download_failed
                }
                Toast.makeText(this@MainActivity, getString(messageRes), Toast.LENGTH_LONG).show()
                openUpdateUrl(updateInfo.apkUrl)
            }
        }
    }

    private fun canRequestPackageInstallsCompat(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
    }

    private fun openUnknownSourcesSettings() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }.onFailure {
            Toast.makeText(this, getString(R.string.update_install_start_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun installDownloadedApk(file: File) {
        runCatching {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }.onFailure {
            Log.w(TAG, "Failed to launch installer", it)
            Toast.makeText(this, getString(R.string.update_install_start_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun openUpdateUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, getString(R.string.update_open_url_failed), Toast.LENGTH_SHORT).show()
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

    override fun onPause() {
        lastBackPressedAtMs = 0L
        super.onPause()
    }

    override fun onDestroy() {
        playbackFailureDialog?.dismiss()
        updateDialog?.dismiss()
        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        binding.channelOverlay.animate().cancel()
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}
