package com.jons.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ChannelRepository(
    private val playlistUrl: String = "http://192.140.163.220:9986/%E5%B1%BF%E9%A3%8E%E7%9C%A0%E6%98%9F%E8%BE%9E%E9%9B%BE%E5%90%AC%E6%BE%9C%E4%B9%A6%E7%A6%BE%E5%BF%B5%E5%AE%89%E7%9F%A5%E5%A4%8F%E9%81%87%E7%A7%8B%E5%AF%BB%E5%86%AC%E8%A7%82%E6%9C%88.txt"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(playlistUrl)
            .header("User-Agent", "Mozilla/5.0 (Android) IPTV/1.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch playlist: HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val channels = runCatching { M3uParser.parse(body) }
                .getOrElse { throw IllegalStateException("Failed to parse playlist", it) }

            if (channels.isEmpty()) {
                throw IllegalStateException("Parsed playlist is empty")
            }

            channels
        }
    }
}
