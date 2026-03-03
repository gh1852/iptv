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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jons.iptv.data.AppUpdateRepository
import com.jons.iptv.data.CategoryChannels
import com.jons.iptv.data.Channel
import com.jons.iptv.data.ChannelRepository
import com.jons.iptv.data.UpdateInfo
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.playback.ExoPlayerAdapter
import com.jons.iptv.playback.PlaybackController
import com.jons.iptv.playback.PlaybackLogger
import com.jons.iptv.playback.PlaybackStore
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
        private const val HTTP_CONNECT_TIMEOUT_MS = 4_000
        private const val HTTP_READ_TIMEOUT_MS = 8_000
        private const val LOAD_ERROR_MIN_RETRY_COUNT = 0
        private const val LOAD_ERROR_NETWORK_RETRY_DELAY_MS = 300L
        private const val LOAD_ERROR_OTHER_RETRY_DELAY_MS = 600L
        private const val BACK_PRESS_EXIT_WINDOW_MS = 2_000L
        private const val STARTUP_MENU_AUTO_HIDE_DELAY_MS = 1_500L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

    private val repository = ChannelRepository()
    private val appUpdateRepository = AppUpdateRepository()
    private val groupedChannelAdapter = GroupedChannelAdapter { channel ->
        onChannelSelected(channel)
    }

    private var menuVisible: Boolean = true
    private var pendingMenuRestoreState: Parcelable? = null
    private var pendingMenuFocusPosition: Int? = null
    private var pendingMenuFocusChannel: Channel? = null

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
    private var lastBackPressedAtMs: Long = 0L
    private var decoderRecoveryRequired: Boolean = false

    private val playbackStore = PlaybackStore()
    private lateinit var playbackController: PlaybackController

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.i(
                TAG,
                "Playback state changed state=$playbackState, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, channel=${currentChannel?.name}, index=$currentStreamIndex, position=${player.currentPosition}"
            )
            playbackController.onPlaybackStateChanged(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(
                TAG,
                "isPlaying changed isPlaying=$isPlaying, state=${player.playbackState}, channel=${currentChannel?.name}, index=$currentStreamIndex, position=${player.currentPosition}"
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            if (isFinishing || isDestroyed) return
            if (isDecoderFailure(error)) {
                decoderRecoveryRequired = true
            }
            playbackController.onPlayerError(error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository.preloadChannels()
        enableFullscreenIfPhone()
        initPlayer()
        initPlaybackController()
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

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!menuVisible) {
                        previousChannel?.let { playChannel(it, 0) }
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!menuVisible) {
                        nextChannel?.let { playChannel(it, 0) }
                        return true
                    }
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
            return triggerMenuFocusedClick(focusedView)
        }

        hideMenu(moveFocusToPlayer = true)
        return true
    }

    private fun triggerMenuFocusedClick(focusedView: View): Boolean {
        var current: View? = focusedView
        while (current != null && current !== binding.menuContainer) {
            if (current.isClickable && current.performClick()) {
                return true
            }
            val parent = current.parent
            current = if (parent is View) parent else null
        }
        return false
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

        trackSelector = DefaultTrackSelector(this).apply {
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

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setKeepPostFor302Redirects(true)
            .setUserAgent("Mozilla/5.0 (Android) IPTV/1.0")

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLiveTargetOffsetMs(3_000)
            .setLoadErrorHandlingPolicy(
                object : DefaultLoadErrorHandlingPolicy(LOAD_ERROR_MIN_RETRY_COUNT) {
                    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                        return LOAD_ERROR_MIN_RETRY_COUNT
                    }

                    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
                        val exception = loadErrorInfo.exception
                        val delayMs = when (exception) {
                            is HttpDataSource.HttpDataSourceException,
                            is HttpDataSource.InvalidResponseCodeException -> LOAD_ERROR_NETWORK_RETRY_DELAY_MS
                            else -> LOAD_ERROR_OTHER_RETRY_DELAY_MS
                        }
                        Log.w(
                            TAG,
                            "Load retry decision errorCount=${loadErrorInfo.errorCount}, exception=${exception.javaClass.simpleName}, delayMs=$delayMs"
                        )
                        return delayMs
                    }
                }
            )

        player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
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
                it.setHandleAudioBecomingNoisy(true)
                it.addListener(playerListener)
                binding.playerView.player = it
            }
    }

    private fun initPlaybackController() {
        val logger = PlaybackLogger(TAG)
        val adapter = ExoPlayerAdapter(player) { url -> buildMediaItem(url) }

        playbackController = PlaybackController(
            store = playbackStore,
            playerAdapter = adapter,
            logger = logger,
            callbacks = object : PlaybackController.Callbacks {
                override fun onPrepareToPlay(channel: Channel, streamIndex: Int, streamUrl: String) {
                    currentChannel = channel
                    currentStreamIndex = streamIndex
                }

                override fun onSwitchingSource(
                    channel: Channel,
                    fromIndex: Int,
                    toIndex: Int,
                    reason: String
                ) {
                    if (!isSwitchingStream) {
                        isSwitchingStream = true
                        Toast.makeText(this@MainActivity, getString(R.string.switching_stream), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStreamPlaying(channel: Channel, streamIndex: Int) {
                    isSwitchingStream = false
                    playbackFailureDialog?.dismiss()
                    groupedChannelAdapter.setPlayingChannel(channel)
                    refreshAdjacentChannels()
                    showOverlay(channel)
                }

                override fun onAllSourcesFailed(channel: Channel) {
                    isSwitchingStream = false
                    showPlaybackFailureDialog(channel)
                }
            }
        )
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
            runCatching { repository.getChannels() }
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

    private fun ensureHealthyPlayerIfNeeded(reason: String) {
        if (!decoderRecoveryRequired) {
            return
        }

        Log.w(TAG, "Recreate player for decoder recovery reason=$reason")
        runCatching {
            player.removeListener(playerListener)
            player.release()
            initPlayer()
            initPlaybackController()
            decoderRecoveryRequired = false
        }.onFailure { throwable ->
            Log.e(TAG, "Player recreation failed reason=$reason", throwable)
            decoderRecoveryRequired = false
        }
    }

    private fun playChannel(channel: Channel, streamIndex: Int) {
        ensureHealthyPlayerIfNeeded("play_channel")
        Log.d(
            TAG,
            "Play channel request channel=${channel.name}, category=${channel.category}, streamIndex=$streamIndex, streamCount=${channel.streamUrls.size}"
        )
        isSwitchingStream = false
        playbackController.play(channel, streamIndex)
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
        pendingMenuFocusChannel = pendingMenuFocusPosition?.let { groupedChannelAdapter.getChannelAtPosition(it) }
    }

    private fun restoreMenuStateOrFocusFirst() {
        val recyclerView = binding.groupedChannelRecycler
        pendingMenuRestoreState?.let { savedState ->
            recyclerView.layoutManager?.onRestoreInstanceState(savedState)
            pendingMenuRestoreState = null
        }

        currentChannel?.let { channel ->
            var playingPosition = groupedChannelAdapter.findPositionByChannel(channel)
            if (playingPosition == null) {
                groupedChannelAdapter.setExpandedGroup(channel.category)
                playingPosition = groupedChannelAdapter.findPositionByChannel(channel)
            }
            if (playingPosition != null && recyclerView.requestItemFocus(playingPosition)) {
                pendingMenuFocusPosition = null
                pendingMenuFocusChannel = null
                return
            }
        }

        val focusPosition = pendingMenuFocusPosition
        pendingMenuFocusPosition = null

        if (focusPosition != null && recyclerView.requestItemFocus(focusPosition)) {
            pendingMenuFocusChannel = null
            return
        }

        val focusChannel = pendingMenuFocusChannel
        pendingMenuFocusChannel = null
        val fallbackPosition = focusChannel?.let { groupedChannelAdapter.findPositionByChannel(it) }
        if (fallbackPosition != null && recyclerView.requestItemFocus(fallbackPosition)) {
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
            centerItemIfPossible(holder.itemView)
            return true
        }

        post {
            val postedHolder = findViewHolderForAdapterPosition(position)
            if (postedHolder?.itemView?.requestFocus() == true) {
                centerItemIfPossible(postedHolder.itemView)
            } else {
                requestFocus()
            }
        }
        return true
    }

    private fun RecyclerView.centerItemIfPossible(itemView: View) {
        post {
            if (!isAttachedToWindow) return@post
            val targetTop = (height - itemView.height) / 2
            val dy = itemView.top - targetTop
            if (dy != 0) {
                scrollBy(0, dy)
            }
        }
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

    private fun isDecoderFailure(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
    }

    private fun buildMediaItem(url: String): MediaItem {
        val inferredMimeType = inferMimeType(url)
        Log.d(TAG, "Build media item url=$url, inferredMime=$inferredMimeType")
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
        playbackController.release()
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}
