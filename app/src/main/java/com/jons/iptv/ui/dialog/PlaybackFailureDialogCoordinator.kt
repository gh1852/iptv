package com.jons.iptv.ui.dialog

import android.app.Dialog
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jons.iptv.R
import com.jons.iptv.data.Channel

class PlaybackFailureDialogCoordinator(
    private val activity: AppCompatActivity,
    private val onRetry: (Channel) -> Unit
) {
    private var playbackFailureDialog: Dialog? = null
    private var playbackFailureDialogAnimatedDismiss: Boolean = false

    fun showPlaybackFailureDialog(channel: Channel) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (playbackFailureDialog?.isShowing == true) return

        playbackFailureDialogAnimatedDismiss = false
        val dialogView = activity.fixedFontScaleInflater().inflate(R.layout.dialog_playback_failure_cctv, null)
        dialogView.findViewById<TextView>(R.id.failureTitle).text = activity.getString(R.string.playback_failed_title)
        dialogView.findViewById<TextView>(R.id.failureMessage).text =
            activity.getString(R.string.playback_failed_message, channel.name)

        val retryButton = dialogView.findViewById<TextView>(R.id.buttonRetry)
        val closeButton = dialogView.findViewById<TextView>(R.id.buttonClose)

        playbackFailureDialog = Dialog(activity).apply {
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
            CctvStyleDialogAnimator.playDialogEnterAnimation(this)

            retryButton.setOnClickListener {
                dismissPlaybackFailureDialog(this) {
                    onRetry(channel)
                }
            }
            closeButton.setOnClickListener {
                dismissPlaybackFailureDialog(this)
            }
        }
    }

    fun dismissPlaybackFailureDialog(dialog: Dialog, onDismissed: (() -> Unit)? = null) {
        if (playbackFailureDialogAnimatedDismiss) return
        playbackFailureDialogAnimatedDismiss = true
        CctvStyleDialogAnimator.playDialogDismissAnimation(dialog) {
            onDismissed?.invoke()
        }
    }

    fun dismissImmediately() {
        playbackFailureDialog?.dismiss()
    }

    fun dismissIfShowing() {
        playbackFailureDialog?.dismiss()
    }
}
