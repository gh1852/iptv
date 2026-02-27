package com.jons.iptv

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.jons.iptv.data.CategoryChannels
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
        selectCategory(category)
    }

    private val channelAdapter = ChannelAdapter { channel ->
        onChannelSelected(channel)
    }

    private var groupedChannels: List<CategoryChannels> = emptyList()
    private var selectedCategory: String? = null
    private var currentChannel: Channel? = null
    private var currentStreamIndex: Int = 0
    private var overlayHideRunnable: Runnable? = null

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (isFinishing || isDestroyed) return
            val channel = currentChannel ?: return
            if (!tryPlayFrom(channel, currentStreamIndex + 1)) {
                Toast.makeText(this@MainActivity, getString(R.string.no_more_streams), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPlayer()
        initLists()
        loadChannels()
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().also {
            it.addListener(playerListener)
            binding.playerView.player = it
        }
    }

    private fun initLists() {
        binding.categoryRecycler.layoutManager = LinearLayoutManager(this)
        binding.channelRecycler.layoutManager = LinearLayoutManager(this)
        binding.categoryRecycler.adapter = categoryAdapter
        binding.channelRecycler.adapter = channelAdapter
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            runCatching { repository.fetchChannels() }
                .onSuccess { channels ->
                    groupedChannels = channels
                        .groupBy { it.category.ifBlank { getString(R.string.category_other) } }
                        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                        .map { (category, list) ->
                            CategoryChannels(
                                category = category,
                                channels = list.sortedBy { it.name.lowercase() }
                            )
                        }

                    val categories = groupedChannels.map { it.category }
                    categoryAdapter.submitList(categories)

                    val first = groupedChannels.firstOrNull()
                    if (first != null) {
                        selectedCategory = first.category
                        categoryAdapter.setSelected(first.category)
                        channelAdapter.submitList(first.channels)
                        first.channels.firstOrNull()?.let { playChannel(it, 0) }
                    }
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, getString(R.string.load_failed), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun selectCategory(category: String) {
        selectedCategory = category
        categoryAdapter.setSelected(category)
        val channels = groupedChannels.firstOrNull { it.category == category }?.channels.orEmpty()
        channelAdapter.submitList(channels)
    }

    private fun onChannelSelected(channel: Channel) {
        playChannel(channel, 0)
    }

    private fun playChannel(channel: Channel, streamIndex: Int) {
        if (!tryPlayFrom(channel, streamIndex)) {
            Toast.makeText(this, getString(R.string.no_more_streams), Toast.LENGTH_SHORT).show()
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
                channelAdapter.setPlayingChannel(channel)
                showOverlay(channel)
                return true
            }
        }

        return false
    }

    private fun showOverlay(channel: Channel) {
        binding.overlayName.text = channel.name
        binding.overlayLogo.load(channel.logoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_channel_placeholder)
            error(R.drawable.ic_channel_placeholder)
        }
        binding.channelOverlay.visibility = View.VISIBLE

        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        overlayHideRunnable = Runnable {
            binding.channelOverlay.visibility = View.GONE
        }.also {
            binding.channelOverlay.postDelayed(it, 3000)
        }
    }

    override fun onDestroy() {
        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}
