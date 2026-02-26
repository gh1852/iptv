package com.jons.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class ChannelRepository(
    private val playlistUrl: String = "https://live.zbds.top/tv/iptv4.m3u"
) {
    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val connection = (URL(playlistUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to fetch playlist: HTTP ${connection.responseCode}")
            }

            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            M3uParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }
}
