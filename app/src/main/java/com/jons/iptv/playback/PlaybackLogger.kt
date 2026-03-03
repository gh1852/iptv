package com.jons.iptv.playback

import android.util.Log

class PlaybackLogger(
    private val tag: String = "PlaybackController"
) {
    fun d(message: String) {
        Log.d(tag, message)
    }

    fun i(message: String) {
        Log.i(tag, message)
    }

    fun w(message: String) {
        Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
