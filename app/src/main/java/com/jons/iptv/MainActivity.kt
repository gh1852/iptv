package com.jons.iptv

import android.os.Bundle
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
import coil.load
import com.jons.iptv.data.CategoryChannels
import com.jons.iptv.data.Channel
import com.jons.iptv.data.ChannelRepository
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.ui.GroupedChannelAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer

    private val repository = ChannelRepository()

    private val groupedChannelAdapter = GroupedChannelAdapter { channel ->
        onChannelSelected(channel)
    }

    private var groupedChannels: List<CategoryChannels> = emptyList()
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
        loadChannels()
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().also {
            it.addListener(playerListener)
            binding.playerView.player = it
        }
    }

    private fun initList() {
        binding.channelRecycler.layoutManager = LinearLayoutManager(this)
        binding.channelRecycler.adapter = groupedChannelAdapter
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

                    groupedChannelAdapter.submitGroups(groupedChannels)

                    groupedChannels
                        .firstOrNull()
                        ?.channels
                        ?.firstOrNull()
                        ?.let { playChannel(it, 0) }
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
                groupedChannelAdapter.setPlayingChannel(channel)
                showOverlay(channel)
                return true
            }
        }

        return false
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
        binding.channelOverlay.visibility = View.VISIBLE

        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        overlayHideRunnable = Runnable {
            binding.channelOverlay.visibility = View.GONE
        }.also {
            binding.channelOverlay.postDelayed(it, 3000)
        }
    }

    override fun onDestroy() {
        playbackFailureDialog?.dismiss()
        overlayHideRunnable?.let { binding.channelOverlay.removeCallbacks(it) }
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}
