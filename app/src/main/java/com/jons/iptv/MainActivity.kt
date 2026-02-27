package com.jons.iptv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jons.iptv.data.Channel
import com.jons.iptv.data.ChannelRepository
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.ui.CategoryAdapter
import com.jons.iptv.ui.ChannelAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer

    private val repository = ChannelRepository()

    private val categoryAdapter = CategoryAdapter { category ->
        updateChannelsForCategory(category, moveFocusToChannels = true)
    }
    private val channelAdapter = ChannelAdapter { channel ->
        onChannelSelected(channel)
        lastMenuFocusTarget = MenuFocusTarget.CHANNEL
    }

    private var menuVisible: Boolean = true
    private var selectedCategory: String? = null
    private var categoryToChannels: Map<String, List<Channel>> = emptyMap()
    private var lastMenuFocusTarget: MenuFocusTarget = MenuFocusTarget.CATEGORY

    private var currentChannel: Channel? = null
    private var currentStreamIndex: Int = 0
    private var overlayHideRunnable: Runnable? = null
    private var playbackFailureDialog: AlertDialog? = null
    private var playbackFailureDialogAnimatedDismiss: Boolean = false
    private var isSwitchingStream: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (isFinishing || isDestroyed) return
            val channel = currentChannel ?: return

            val currentUrl = channel.streamUrls.getOrNull(currentStreamIndex)
            Log.w(
                TAG,
                "Playback error channel=${channel.name}, index=$currentStreamIndex, url=$currentUrl, code=${error.errorCodeName}",
                error
            )

            if (!shouldAutoSwitch(error)) {
                Log.w(TAG, "Skip auto-switch for error code=${error.errorCodeName}")
                return
            }

            if (tryPlayFrom(channel, currentStreamIndex + 1)) {
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
        player = ExoPlayer.Builder(this).build().also {
            it.addListener(playerListener)
            binding.playerView.player = it
        }
    }

    private fun initList() {
        binding.categoryRecycler.layoutManager = LinearLayoutManager(this)
        binding.categoryRecycler.adapter = categoryAdapter

        binding.channelListRecycler.layoutManager = LinearLayoutManager(this)
        binding.channelListRecycler.adapter = channelAdapter
    }

    private fun initMenuInteractions() {
        binding.categoryRecycler.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) lastMenuFocusTarget = MenuFocusTarget.CATEGORY
        }
        binding.channelListRecycler.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) lastMenuFocusTarget = MenuFocusTarget.CHANNEL
        }

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
                    categoryToChannels = channels
                        .groupBy { it.category.ifBlank { getString(R.string.category_other) } }
                        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                        .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }

                    val categories = categoryToChannels.keys.toList()
                    categoryAdapter.submitList(categories)

                    val initialCategory = categories.firstOrNull()
                    if (initialCategory != null) {
                        updateChannelsForCategory(initialCategory, moveFocusToChannels = false)
                    } else {
                        selectedCategory = null
                        channelAdapter.submitList(emptyList())
                        binding.channelListRecycler.visibility = View.GONE
                    }

                    categoryToChannels[initialCategory]
                        ?.firstOrNull()
                        ?.let { playChannel(it, 0) }

                    showMenu(MenuFocusTarget.CATEGORY)
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun onChannelSelected(channel: Channel) {
        playChannel(channel, 0)
    }

    private fun playChannel(channel: Channel, streamIndex: Int) {
        isSwitchingStream = false
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
                channelAdapter.setPlayingChannel(channel)
                showOverlay(channel)
                return true
            }
        }

        Log.w(TAG, "All playback setup attempts failed channel=${channel.name}, startIndex=$startIndex")
        return false
    }


    private fun updateChannelsForCategory(category: String, moveFocusToChannels: Boolean) {
        selectedCategory = category
        categoryAdapter.setSelected(category)

        val categoryChannels = categoryToChannels[category].orEmpty()
        channelAdapter.submitList(categoryChannels)
        binding.channelListRecycler.visibility = if (categoryChannels.isEmpty()) View.GONE else View.VISIBLE

        if (moveFocusToChannels && categoryChannels.isNotEmpty()) {
            lastMenuFocusTarget = MenuFocusTarget.CHANNEL
            requestFirstItemFocus(binding.channelListRecycler)
        } else {
            lastMenuFocusTarget = MenuFocusTarget.CATEGORY
        }
    }

    private fun showMenu(target: MenuFocusTarget? = null) {
        menuVisible = true
        binding.menuContainer.visibility = View.VISIBLE

        val focusTarget = target ?: lastMenuFocusTarget
        if (focusTarget == MenuFocusTarget.CHANNEL && channelAdapter.itemCount > 0 && binding.channelListRecycler.visibility == View.VISIBLE) {
            requestFirstItemFocus(binding.channelListRecycler)
        } else {
            requestFirstItemFocus(binding.categoryRecycler)
        }
    }

    private fun hideMenu(moveFocusToPlayer: Boolean = true) {
        menuVisible = false
        binding.menuContainer.visibility = View.GONE
        if (moveFocusToPlayer) {
            binding.playerContainer.requestFocus()
        }
    }

    private fun requestFirstItemFocus(recyclerView: RecyclerView) {
        recyclerView.post {
            val itemCount = recyclerView.adapter?.itemCount ?: 0
            if (itemCount == 0) {
                recyclerView.requestFocus()
                return@post
            }
            recyclerView.scrollToPosition(0)
            recyclerView.post {
                val holder = recyclerView.findViewHolderForAdapterPosition(0)
                if (holder?.itemView?.requestFocus() != true) {
                    recyclerView.requestFocus()
                }
            }
        }
    }

    private fun showPlaybackFailureDialog(channel: Channel) {
        if (isFinishing || isDestroyed) return
        if (playbackFailureDialog?.isShowing == true) return

        playbackFailureDialogAnimatedDismiss = false
        playbackFailureDialog = AlertDialog.Builder(this)
            .setTitle(R.string.playback_failed_title)
            .setMessage(getString(R.string.playback_failed_message, channel.name))
            .setCancelable(true)
            .setPositiveButton(R.string.retry, null)
            .setNegativeButton(R.string.close, null)
            .show()
            .also { dialog ->
                val buttonColor = ContextCompat.getColor(this, R.color.dialog_button_tint)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)

                dialog.window?.setBackgroundDrawableResource(R.drawable.bg_overlay)
                playDialogEnterAnimation(dialog)

                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    dismissPlaybackFailureDialog(dialog) {
                        playChannel(channel, 0)
                    }
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
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

    private fun buildMediaItem(url: String): MediaItem {
        return MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
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

    private enum class MenuFocusTarget {
        CATEGORY,
        CHANNEL
    }
}
