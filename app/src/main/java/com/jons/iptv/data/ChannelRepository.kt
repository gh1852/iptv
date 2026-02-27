package com.jons.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ChannelRepository(
    private val playlistUrl: String = "http://192.140.163.220:9986/%E5%B1%BF%E9%A3%8E%E7%9C%A0%E6%98%9F%E8%BE%9E%E9%9B%BE%E5%90%AC%E6%BE%9C%E4%B9%A6%E7%A6%BE%E5%BF%B5%E5%AE%89%E7%9F%A5%E5%A4%8F%E9%81%87%E7%A7%8B%E5%AF%BB%E5%86%AC%E8%A7%82%E6%9C%88.txt",
    private val logoBaseUrl: String = "https://live.fanmingming.cn/tv"
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

            val candidates = candidateKeys(channel.name)
                .mapNotNull(::buildLogoUrl)
                .distinct()

            if (candidates.size != 1) {
                return@map channel
            }

            channel.copy(logoUrl = candidates.first())
        }
    }

    private fun normalizeChannelName(name: String): String {
        val trimmed = name.trim().lowercase()
        if (trimmed.isEmpty()) return ""

        return buildString(trimmed.length) {
            for (ch in trimmed) {
                if (ch.isLetterOrDigit()) {
                    append(ch)
                }
            }
        }
    }

    private fun candidateKeys(channelName: String): List<String> {
        val normalized = normalizeChannelName(channelName)
        if (normalized.isBlank()) return emptyList()

        val keys = linkedSetOf(normalized)
        CHANNEL_ALIASES[normalized]?.let(keys::add)

        val withoutSuffix = normalized
            .removeSuffix("高清")
            .removeSuffix("标清")
            .removeSuffix("频道")
            .removeSuffix("台")
            .removeSuffix("hd")

        if (withoutSuffix.isNotBlank()) {
            keys.add(withoutSuffix)
            CHANNEL_ALIASES[withoutSuffix]?.let(keys::add)
        }

        return keys.toList()
    }

    private fun buildLogoUrl(key: String): String? {
        val logoName = LOGO_NAME_MAP[key] ?: key.uppercase()
        if (logoName.isBlank()) return null
        return "$logoBaseUrl/$logoName.png"
    }

    companion object {
        private val CHANNEL_ALIASES = mapOf(
            "cctv1综合" to "cctv1",
            "cctv2财经" to "cctv2",
            "cctv3综艺" to "cctv3",
            "cctv4中文国际" to "cctv4",
            "cctv5体育" to "cctv5",
            "cctv5体育赛事" to "cctv5plus",
            "cctv5plus体育赛事" to "cctv5plus",
            "cctv6电影" to "cctv6",
            "cctv7国防军事" to "cctv7",
            "cctv8电视剧" to "cctv8",
            "cctv9纪录" to "cctv9",
            "cctv10科教" to "cctv10",
            "cctv11戏曲" to "cctv11",
            "cctv12社会与法" to "cctv12",
            "cctv13新闻" to "cctv13",
            "cctv14少儿" to "cctv14",
            "cctv15音乐" to "cctv15",
            "cctv16奥林匹克" to "cctv16",
            "cctv17农业农村" to "cctv17"
        )

        private val LOGO_NAME_MAP = mapOf(
            "cctv1" to "CCTV1",
            "cctv2" to "CCTV2",
            "cctv3" to "CCTV3",
            "cctv4" to "CCTV4",
            "cctv5" to "CCTV5",
            "cctv5plus" to "CCTV5+",
            "cctv6" to "CCTV6",
            "cctv7" to "CCTV7",
            "cctv8" to "CCTV8",
            "cctv9" to "CCTV9",
            "cctv10" to "CCTV10",
            "cctv11" to "CCTV11",
            "cctv12" to "CCTV12",
            "cctv13" to "CCTV13",
            "cctv14" to "CCTV14",
            "cctv15" to "CCTV15",
            "cctv16" to "CCTV16",
            "cctv17" to "CCTV17"
        )
    }
}
