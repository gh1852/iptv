package com.jons.iptv.update

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import com.jons.iptv.R
import com.jons.iptv.data.AppUpdateRepository
import com.jons.iptv.data.UpdateInfo
import com.jons.iptv.ui.dialog.CctvStyleDialogAnimator
import kotlinx.coroutines.launch
import java.io.File

class AppUpdateCoordinator(
    private val activity: AppCompatActivity,
    private val appUpdateRepository: AppUpdateRepository,
    private val logTag: String
) {
    private var updateDialog: Dialog? = null
    private var progressDialog: Dialog? = null
    private var updateDialogAnimatedDismiss: Boolean = false

    private enum class PendingUpdateStep {
        NEED_PERMISSION_BEFORE_DOWNLOAD,
        NEED_PERMISSION_BEFORE_INSTALL
    }

    private var pendingUpdateInfo: UpdateInfo? = null
    private var pendingApkFile: File? = null
    private var pendingStep: PendingUpdateStep? = null

    fun checkUpdateSilently() {
        activity.lifecycleScope.launch {
            runCatching { appUpdateRepository.fetchLatest() }
                .onSuccess { latest ->
                    val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
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
                    Log.w(logTag, "Update check failed", throwable)
                }
        }
    }

    fun showUpdateDialog(updateInfo: UpdateInfo, forceUpdate: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (updateDialog?.isShowing == true) return

        val message = if (!updateInfo.changelog.isNullOrBlank()) {
            activity.getString(
                R.string.update_dialog_message_with_changelog,
                updateInfo.versionName,
                updateInfo.changelog
            )
        } else {
            activity.getString(R.string.update_dialog_message_no_changelog, updateInfo.versionName)
        }

        updateDialogAnimatedDismiss = false
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_playback_failure_cctv, null)
        dialogView.findViewById<TextView>(R.id.failureTitle).text = activity.getString(R.string.update_dialog_title)
        dialogView.findViewById<TextView>(R.id.failureMessage).text = message

        val nowButton = dialogView.findViewById<TextView>(R.id.buttonRetry)
        val laterButton = dialogView.findViewById<TextView>(R.id.buttonClose)
        nowButton.text = activity.getString(R.string.update_action_now)
        laterButton.text = activity.getString(R.string.update_action_later)
        laterButton.visibility = if (forceUpdate) View.GONE else View.VISIBLE

        updateDialog = Dialog(activity).apply {
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
                activity.resources.getDimensionPixelSize(R.dimen.update_dialog_fixed_width),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            CctvStyleDialogAnimator.playDialogEnterAnimation(this)

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

    fun dismissUpdateDialog(dialog: Dialog, onDismissed: (() -> Unit)? = null) {
        if (updateDialogAnimatedDismiss) return
        updateDialogAnimatedDismiss = true
        CctvStyleDialogAnimator.playDialogDismissAnimation(dialog) {
            onDismissed?.invoke()
        }
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!canRequestPackageInstallsCompat()) {
            pendingUpdateInfo = updateInfo
            pendingStep = PendingUpdateStep.NEED_PERMISSION_BEFORE_DOWNLOAD
            Toast.makeText(
                activity,
                activity.getString(R.string.update_install_permission_required),
                Toast.LENGTH_LONG
            ).show()
            openUnknownSourcesSettings()
            return
        }

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_update_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.progressPercent)

        progressDialog = Dialog(activity).apply {
            setContentView(dialogView)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                activity.resources.getDimensionPixelSize(R.dimen.update_dialog_fixed_width),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            show()
        }

        activity.lifecycleScope.launch {
            val updateDir = File(activity.cacheDir, "updates")
            val targetFile = File(updateDir, "iptv-update-${updateInfo.versionCode}.apk")

            runCatching {
                val downloaded = appUpdateRepository.downloadApk(updateInfo.apkUrl, targetFile) { downloaded, total ->
                    activity.runOnUiThread {
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        progressBar.progress = percent
                        progressText.text = activity.getString(R.string.update_download_progress, percent)
                    }
                }
                appUpdateRepository.verifySha256(downloaded, updateInfo.sha256)
                downloaded
            }.onSuccess { apkFile ->
                progressDialog?.dismiss()
                if (!canRequestPackageInstallsCompat()) {
                    pendingApkFile = apkFile
                    pendingStep = PendingUpdateStep.NEED_PERMISSION_BEFORE_INSTALL
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.update_install_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
                    openUnknownSourcesSettings()
                    return@onSuccess
                }
                installDownloadedApk(apkFile)
            }.onFailure { throwable ->
                progressDialog?.dismiss()
                clearPendingUpdate()
                Log.w(logTag, "Update install flow failed", throwable)
                val messageRes = if (throwable.message?.contains("SHA256", ignoreCase = true) == true) {
                    R.string.update_verification_failed
                } else {
                    R.string.update_download_failed
                }
                Toast.makeText(activity, activity.getString(messageRes), Toast.LENGTH_LONG).show()
                openUpdateUrl(updateInfo.apkUrl)
            }
        }
    }

    fun canRequestPackageInstallsCompat(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
    }

    fun resumePendingUpdateIfNeeded() {
        if (activity.isFinishing || activity.isDestroyed) return
        val step = pendingStep ?: return
        if (!canRequestPackageInstallsCompat()) return

        when (step) {
            PendingUpdateStep.NEED_PERMISSION_BEFORE_DOWNLOAD -> {
                val updateInfo = pendingUpdateInfo ?: return
                clearPendingUpdate()
                downloadAndInstallUpdate(updateInfo)
            }
            PendingUpdateStep.NEED_PERMISSION_BEFORE_INSTALL -> {
                val apkFile = pendingApkFile ?: return
                clearPendingUpdate()
                installDownloadedApk(apkFile)
            }
        }
    }

    private fun clearPendingUpdate() {
        pendingUpdateInfo = null
        pendingApkFile = null
        pendingStep = null
    }

    fun openUnknownSourcesSettings() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
        }.onFailure {
            clearPendingUpdate()
            Toast.makeText(activity, activity.getString(R.string.update_install_start_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun installDownloadedApk(file: File) {
        runCatching {
            val apkUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
        }.onFailure {
            clearPendingUpdate()
            Log.w(logTag, "Failed to launch installer", it)
            Toast.makeText(activity, activity.getString(R.string.update_install_start_failed), Toast.LENGTH_LONG).show()
        }
    }

    fun openUpdateUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        }.onFailure {
            Toast.makeText(activity, activity.getString(R.string.update_open_url_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissIfShowing() {
        updateDialog?.dismiss()
    }
}
