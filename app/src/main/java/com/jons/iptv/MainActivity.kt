package com.jons.iptv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
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

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (isFinishing || isDestroyed) return
            val channel = currentChannel ?: return
            if (!tryPlayFrom(channel, currentStreamIndex + 1)) {
                showPlaybackFailureDialog(channel)
            }
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
        if (!tryPlayFrom(channel, streamIndex)) {
            showPlaybackFailureDialog(channel)
        }
    }

    private fun tryPlayFrom(channel: Channel, startIndex: Int): Boolean {
        if (channel.streamUrls.isEmpty() || startIndex !in channel.streamUrls.indices) return false

        for (index in startIndex until channel.streamUrls.size) {
            val played = runCatching {
                currentChannel = channel
                currentStreamIndex = index
                val mediaItem = MediaItem.fromUri(channel.streamUrls[index])
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }.isSuccess

            if (played) {
                playbackFailureDialog?.dismiss()
                channelAdapter.setPlayingChannel(channel)
                showOverlay(channel)
                return true
            }
        }

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

        playbackFailureDialog = AlertDialog.Builder(this)
            .setTitle(R.string.playback_failed_title)
            .setMessage(getString(R.string.playback_failed_message, channel.name))
            .setCancelable(true)
            .setPositiveButton(R.string.retry) { _, _ ->
                playChannel(channel, 0)
            }
            .setNegativeButton(R.string.close, null)
            .show()
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
            binding.channelOverlay.visibility = View.VISIBLE
        }
        binding.channelOverlay.animate()
            .alpha(1f)
            .setDuration(180L)
            .start()

        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        overlayHideRunnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                binding.channelOverlay.animate().cancel()
                binding.channelOverlay.animate()
                    .alpha(0f)
                    .setDuration(220L)
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
