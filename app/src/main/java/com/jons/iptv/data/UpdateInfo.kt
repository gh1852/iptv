package com.jons.iptv.data

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String?,
    val force: Boolean,
    val minSupportedVersionCode: Int
)
