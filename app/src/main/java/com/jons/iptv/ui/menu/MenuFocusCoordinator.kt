package com.jons.iptv.ui.menu

import android.os.Parcelable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.jons.iptv.data.Channel
import com.jons.iptv.databinding.ActivityMainBinding
import com.jons.iptv.ui.GroupedChannelAdapter

class MenuFocusCoordinator(
    private val binding: ActivityMainBinding,
    private val groupedChannelAdapter: GroupedChannelAdapter,
    private val currentChannelProvider: () -> Channel?
) {
    private var menuVisible: Boolean = true
    private var pendingMenuRestoreState: Parcelable? = null
    private var pendingMenuFocusPosition: Int? = null
    private var pendingMenuFocusChannel: Channel? = null

    fun isMenuVisible(): Boolean = menuVisible

    fun showMenu() {
        menuVisible = true
        binding.menuContainer.visibility = View.VISIBLE
        restoreMenuStateOrFocusFirst()
    }

    fun hideMenu(moveFocusToPlayer: Boolean = true) {
        menuVisible = false
        saveMenuState()
        binding.menuContainer.visibility = View.GONE
        if (moveFocusToPlayer) {
            binding.playerContainer.requestFocus()
        }
    }

    fun toggleMenuVisibility(moveFocusToPlayerWhenHide: Boolean) {
        if (menuVisible) {
            hideMenu(moveFocusToPlayer = moveFocusToPlayerWhenHide)
        } else {
            showMenu()
        }
    }

    fun handleMenuConfirmKey(currentFocus: View?): Boolean {
        if (!menuVisible) {
            showMenu()
            return true
        }

        if (currentFocus != null && isDescendantOf(currentFocus, binding.menuContainer)) {
            return triggerMenuFocusedClick(currentFocus)
        }

        hideMenu(moveFocusToPlayer = true)
        return true
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

        currentChannelProvider()?.let { channel ->
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

    private fun requestFirstItemFocus(recyclerView: RecyclerView) {
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount == 0) {
            recyclerView.requestFocus()
            return
        }
        recyclerView.requestItemFocus(0)
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
}
