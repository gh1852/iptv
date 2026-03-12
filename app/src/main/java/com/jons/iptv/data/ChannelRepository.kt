package com.jons.iptv.data

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ChannelRepository(
    private val playlistUrl: String = "https://raw.githubusercontent.com/gh1852/iptv-api/refs/heads/master/output/result.txt",
    private val logoBaseUrl: String = "https://ghfast.top/https://raw.githubusercontent.com/CCSH/IPTV/main/logo"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private fun buildProxyCandidates(url: String): List<String> {
        val normalized = url.trim()
        if (normalized.isBlank()) return emptyList()

        val raw = stripKnownProxyPrefix(normalized)
        val candidates = linkedSetOf<String>()

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            candidates.add(normalized)
        }

        PROXY_PREFIXES.forEach { prefix ->
            candidates.add("$prefix$raw")
        }
        candidates.add(raw)
        return candidates.toList()
    }

    private fun stripKnownProxyPrefix(url: String): String {
        return PROXY_PREFIXES.firstOrNull { prefix -> url.startsWith(prefix) }
            ?.let { prefix -> url.removePrefix(prefix) }
            ?: url
    }

    fun preloadChannels() {
        synchronized(preloadLock) {
            if (preloadDeferred?.isActive == true || cachedChannels != null) {
                return
            }
            preloadDeferred = preloadScope.async {
                runCatching { fetchChannels() }
            }
        }
    }

    suspend fun getChannels(): List<Channel> {
        val cached = cachedChannels
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        val deferred = synchronized(preloadLock) { preloadDeferred }
        if (deferred != null) {
            val preloadResult = deferred.await()
            val preloaded = preloadResult.getOrNull()

            synchronized(preloadLock) {
                if (preloadDeferred === deferred) {
                    preloadDeferred = null
                }
            }

            if (!preloaded.isNullOrEmpty()) {
                cachedChannels = preloaded
                return preloaded
            }

            preloadResult.exceptionOrNull()?.let { error ->
                throw IllegalStateException("Preload fetch failed", error)
            }
        }

        return fetchChannels().also { channels ->
            if (channels.isNotEmpty()) {
                cachedChannels = channels
            }
        }
    }


    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null

        for (candidateUrl in buildProxyCandidates(playlistUrl)) {
            val request = Request.Builder()
                .url(candidateUrl)
                .header("User-Agent", buildUserAgent())
                .get()
                .build()

            val result = runCatching {
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

            result.onSuccess { return@withContext it }
                .onFailure { lastError = it }
        }

        throw IllegalStateException("Failed to fetch playlist from all proxy candidates", lastError)
    }

    private fun enrichMissingLogos(channels: List<Channel>): List<Channel> {
        if (logoBaseUrl.isBlank()) return channels

        return channels.map { channel ->
            val currentLogoUrl = channel.logoUrl
            if (currentLogoUrl != null && currentLogoUrl.isNotBlank()) {
                return@map channel
            }

            val logoUrl = buildLogoUrls(channel.name).firstOrNull() ?: return@map channel
            channel.copy(logoUrl = logoUrl)
        }
    }

    private fun buildLogoUrls(channelName: String): List<String> {
        val rawName = channelName.trim()
        if (rawName.isBlank()) return emptyList()

        val candidates = linkedSetOf<String>()

        Regex("(?i)cctv\\s*([0-9]{1,2})(\\+)?").find(rawName)?.let { match ->
            val number = match.groupValues[1]
            val plus = match.groupValues[2]
            candidates.add("CCTV$number$plus")
        }

        val withoutDecorations = rawName
            .replace(Regex("\\s+"), "")
            .removeSuffix("高清")
            .removeSuffix("标清")
            .removeSuffix("超清")
            .removeSuffix("频道")
            .removeSuffix("台")
            .removeSuffix("综合")
            .removeSuffix("体育赛事")
            .removeSuffix("体育")
            .removeSuffix("电视剧")
            .removeSuffix("中文国际")
            .removeSuffix("国防军事")
            .removeSuffix("农业农村")
            .removeSuffix("新闻")
            .removeSuffix("少儿")
            .removeSuffix("音乐")
            .removeSuffix("科教")
            .removeSuffix("纪录")
            .removeSuffix("电影")
            .removeSuffix("财经")
            .removeSuffix("综艺")
            .removeSuffix("戏曲")
            .removeSuffix("社会与法")

        if (withoutDecorations.isNotBlank()) {
            candidates.add(withoutDecorations)
        }

        candidates.add(rawName)

        return candidates.mapNotNull { buildLogoUrl(it) }
    }


    private fun buildLogoUrl(channelName: String): String? {
        val trimmedName = channelName.trim()
        if (trimmedName.isBlank()) return null
        val encodedName = URLEncoder.encode(trimmedName, "UTF-8")
            .replace("+", "%20")
        return "$logoBaseUrl/$encodedName.png"
    }

    private fun buildUserAgent(): String {
        return "com.android.chrome/131.0.6778.200 (Linux;Android ${Build.VERSION.RELEASE}) AndroidXMedia3/$MEDIA3_VERSION"
    }

    companion object {
        private const val MEDIA3_VERSION = "1.6.0"
        private val PROXY_PREFIXES = listOf(
            "https://wget.la/",
            "https://ghfast.top/"
        )
        private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val preloadLock = Any()

        @Volatile
        private var preloadDeferred: Deferred<Result<List<Channel>>>? = null

        @Volatile
        private var cachedChannels: List<Channel>? = null
    }
}
