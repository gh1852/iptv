package com.jons.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ChannelRepository(
    private val playlistUrl: String = "http://192.140.163.220:9986/%E5%B1%BF%E9%A3%8E%E7%9C%A0%E6%98%9F%E8%BE%9E%E9%9B%BE%E5%90%AC%E6%BE%9C%E4%B9%A6%E7%A6%BE%E5%BF%B5%E5%AE%89%E7%9F%A5%E5%A4%8F%E9%81%87%E7%A7%8B%E5%AF%BB%E5%86%AC%E8%A7%82%E6%9C%88.txt",
    private val logoBaseUrl: String = "https://wget.la/https://raw.githubusercontent.com/CCSH/IPTV/main/logo"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

            enrichMissingLogos(channels)
        }
    }

    private fun enrichMissingLogos(channels: List<Channel>): List<Channel> {
        if (logoBaseUrl.isBlank()) return channels

        return channels.map { channel ->
            if (!channel.logoUrl.isNullOrBlank()) {
                return@map channel
            }

            val logoUrl = buildLogoUrl(channel.name) ?: return@map channel
            channel.copy(logoUrl = logoUrl)
        }
    }

    private fun buildLogoUrl(channelName: String): String? {
        val trimmedName = channelName.trim()
        if (trimmedName.isBlank()) return null
        val encodedName = URLEncoder.encode(trimmedName, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        return "$logoBaseUrl/$encodedName.png"
    }
}
