package com.jons.iptv.ui.dialog

import android.app.Dialog
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator

object CctvStyleDialogAnimator {
    fun playDialogEnterAnimation(dialog: Dialog) {
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

    fun playDialogDismissAnimation(dialog: Dialog, onDismissed: () -> Unit) {
        val decorView = dialog.window?.decorView
        val content = decorView?.findViewById<View>(android.R.id.content) ?: decorView
        if (content == null) {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            onDismissed()
            return
        }

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
                onDismissed()
            }
            .start()
    }
}
