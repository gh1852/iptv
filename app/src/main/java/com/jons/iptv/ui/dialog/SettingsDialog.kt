package com.jons.iptv.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.jons.iptv.R
import com.jons.iptv.update.AppUpdateCoordinator

class SettingsDialog(
    private val context: Context,
    private val isAutoUpdateEnabled: () -> Boolean,
    private val onAutoUpdateChanged: (Boolean) -> Unit,
    private val onCheckUpdateNow: (onResult: (AppUpdateCoordinator.CheckUpdateResult) -> Unit) -> Unit
) {
    private var dialog: Dialog? = null

    fun show() {
        val view = context.fixedFontScaleInflater().inflate(R.layout.dialog_settings, null)

        val tvVersion = view.findViewById<TextView>(R.id.tvVersion)
        val switchAutoUpdate = view.findViewById<SwitchMaterial>(R.id.switchAutoUpdate)
        val btnCheckUpdate = view.findViewById<TextView>(R.id.btnCheckUpdate)
        val tvCheckResult = view.findViewById<TextView>(R.id.tvCheckResult)

        // 版本号
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("")
        tvVersion.text = context.getString(R.string.app_version, versionName)

        // 自动更新开关
        switchAutoUpdate.isChecked = isAutoUpdateEnabled()
        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            onAutoUpdateChanged(isChecked)
        }

        // 手动检查更新
        btnCheckUpdate.setOnClickListener {
            btnCheckUpdate.isEnabled = false
            btnCheckUpdate.alpha = 0.5f
            tvCheckResult.text = context.getString(R.string.update_checking)
            tvCheckResult.visibility = android.view.View.VISIBLE
            onCheckUpdateNow { result ->
                when (result) {
                    AppUpdateCoordinator.CheckUpdateResult.HAS_UPDATE -> {
                        dismiss()
                    }
                    AppUpdateCoordinator.CheckUpdateResult.NO_UPDATE -> {
                        tvCheckResult.text = context.getString(R.string.already_latest)
                        btnCheckUpdate.isEnabled = false
                        btnCheckUpdate.alpha = 0.3f
                    }
                    AppUpdateCoordinator.CheckUpdateResult.FAILED -> {
                        tvCheckResult.text = context.getString(R.string.update_check_failed)
                        btnCheckUpdate.isEnabled = true
                        btnCheckUpdate.alpha = 1f
                    }
                }
            }
        }

        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
