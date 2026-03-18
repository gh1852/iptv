package com.jons.iptv.ui.dialog

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater

fun Context.fixedFontScaleInflater(): LayoutInflater {
    val config = Configuration(resources.configuration).apply { fontScale = 1.0f }
    val wrapper = object : ContextThemeWrapper(this, theme) {
        override fun getResources() = createConfigurationContext(config).resources
    }
    return LayoutInflater.from(wrapper)
}
