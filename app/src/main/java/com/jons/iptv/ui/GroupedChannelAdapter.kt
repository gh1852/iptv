package com.jons.iptv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jons.iptv.R
import com.jons.iptv.data.CategoryChannels
import com.jons.iptv.data.Channel
import com.jons.iptv.databinding.ItemChannelBinding
import com.jons.iptv.databinding.ItemGroupHeaderBinding

class GroupedChannelAdapter(
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class RowItem {
        data class GroupHeader(
            val category: String,
            val channelCount: Int,
            val expanded: Boolean
        ) : RowItem()

        data class ChannelRow(
            val channel: Channel
        ) : RowItem()
    }

    private val groups = mutableListOf<CategoryChannels>()
    private val expandedGroups = mutableSetOf<String>()
    private val rows = mutableListOf<RowItem>()
    private var playingChannelName: String? = null

    fun getChannelAtPosition(position: Int): Channel? {
        val row = rows.getOrNull(position) as? RowItem.ChannelRow ?: return null
        return row.channel
    }

    fun findPositionByChannel(channel: Channel): Int? {
        val exactIndex = rows.indexOfFirst { row ->
            val channelRow = row as? RowItem.ChannelRow ?: return@indexOfFirst false
            channelRow.channel.category == channel.category && channelRow.channel.name == channel.name
        }
        if (exactIndex >= 0) {
            return exactIndex
        }

        val byNameIndex = rows.indexOfFirst { row ->
            val channelRow = row as? RowItem.ChannelRow ?: return@indexOfFirst false
            channelRow.channel.name == channel.name
        }
        return byNameIndex.takeIf { it >= 0 }
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is RowItem.GroupHeader -> VIEW_TYPE_GROUP_HEADER
            is RowItem.ChannelRow -> VIEW_TYPE_CHANNEL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP_HEADER -> {
                val binding = ItemGroupHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                GroupHeaderViewHolder(binding)
            }

            else -> {
                val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ChannelViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupHeaderViewHolder -> holder.bind(rows[position] as RowItem.GroupHeader)
            is ChannelViewHolder -> holder.bind(rows[position] as RowItem.ChannelRow)
        }
    }

    override fun getItemCount(): Int = rows.size

    fun submitGroups(newGroups: List<CategoryChannels>) {
        groups.clear()
        groups.addAll(newGroups)

        val categories = newGroups.map { it.category }.toSet()
        expandedGroups.retainAll(categories)

        rebuildRows()
        notifyDataSetChanged()
    }

    fun setPlayingChannel(channel: Channel) {
        playingChannelName = channel.name
        notifyDataSetChanged()
    }

    fun setExpandedGroup(category: String) {
        expandedGroups.clear()
        expandedGroups.add(category)
        rebuildRows()
        notifyDataSetChanged()
    }

    fun getAdjacentChannels(current: Channel?): Pair<Channel?, Channel?> {
        val allChannels = groups.flatMap { it.channels }
        if (allChannels.isEmpty() || current == null) {
            return null to null
        }

        val exactIndex = allChannels.indexOfFirst {
            it.category == current.category && it.name == current.name
        }
        val currentIndex = if (exactIndex >= 0) {
            exactIndex
        } else {
            allChannels.indexOfFirst { it.name == current.name }
        }
        if (currentIndex < 0) {
            return null to null
        }

        val size = allChannels.size
        val previous = allChannels[(currentIndex - 1 + size) % size]
        val next = allChannels[(currentIndex + 1) % size]
        return previous to next
    }

    private fun rebuildRows() {
        rows.clear()
        groups.forEach { group ->
            val expanded = expandedGroups.contains(group.category)
            rows.add(
                RowItem.GroupHeader(
                    category = group.category,
                    channelCount = group.channels.size,
                    expanded = expanded
                )
            )
            if (expanded) {
                rows.addAll(group.channels.map { RowItem.ChannelRow(it) })
            }
        }
    }

    private inner class GroupHeaderViewHolder(
        private val binding: ItemGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RowItem.GroupHeader) {
            binding.groupName.text = binding.root.context.getString(
                R.string.group_header_title,
                item.category,
                item.channelCount
            )
            binding.groupArrow.text = if (item.expanded) "▼" else "▶"
            binding.root.setOnClickListener {
                if (expandedGroups.contains(item.category)) {
                    expandedGroups.remove(item.category)
                } else {
                    expandedGroups.add(item.category)
                }
                rebuildRows()
                notifyDataSetChanged()
            }
        }
    }

    private inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RowItem.ChannelRow) {
            val channel = item.channel
            binding.channelName.text = channel.name
            binding.channelLogo.load(channel.logoUrl) {
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
                networkCachePolicy(coil.request.CachePolicy.ENABLED)
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
            }
            binding.root.isSelected = channel.name == playingChannelName
            binding.root.setOnClickListener { onChannelClick(channel) }
        }
    }

    companion object {
        private const val VIEW_TYPE_GROUP_HEADER = 0
        private const val VIEW_TYPE_CHANNEL = 1
    }
}
