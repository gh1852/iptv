package com.jons.iptv.input

import android.view.KeyEvent

class MainKeyEventRouter(
    private val backPressExitWindowMs: Long,
    private val isMenuVisible: () -> Boolean,
    private val hideMenu: (moveFocusToPlayer: Boolean) -> Unit,
    private val playNextChannel: () -> Unit,
    private val playPreviousChannel: () -> Unit,
    private val handleMenuConfirmKey: () -> Boolean,
    private val finishActivity: () -> Unit,
    private val showPressBackAgainToast: () -> Unit
) {
    private var lastBackPressedAtMs: Long = 0L

    fun handle(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) {
            return false
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isMenuVisible()) {
                    hideMenu(true)
                    return true
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressedAtMs <= backPressExitWindowMs) {
                    finishActivity()
                } else {
                    lastBackPressedAtMs = now
                    showPressBackAgainToast()
                }
                return true
            }

            KeyEvent.KEYCODE_CHANNEL_UP -> {
                playNextChannel()
                return true
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                playPreviousChannel()
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!isMenuVisible()) {
                    playPreviousChannel()
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isMenuVisible()) {
                    playNextChannel()
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                return handleMenuConfirmKey()
            }
        }
        return false
    }

    fun resetBackPressWindow() {
        lastBackPressedAtMs = 0L
    }
}
