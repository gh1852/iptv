package com.jons.iptv.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AppUpdateRepository(
    private val metadataUrl: String = "https://github.com/gh1852/iptv/releases/latest/download/latest.json"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null

        for (candidateUrl in buildProxyCandidates(metadataUrl)) {
            val request = Request.Builder()
                .url(candidateUrl)
                .header("User-Agent", buildUserAgent())
                .get()
                .build()

            val result = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Failed to fetch update metadata: HTTP ${response.code}")
                    }

                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)

                    UpdateInfo(
                        versionCode = json.getInt("versionCode"),
                        versionName = json.getString("versionName"),
                        apkUrl = json.getString("apkUrl"),
                        sha256 = json.optString("sha256", ""),
                        changelog = json.optString("changelog", ""),
                        force = json.optBoolean("force", false),
                        minSupportedVersionCode = json.optInt("minSupportedVersionCode", 1)
                    )
                }
            }

            result.onSuccess { return@withContext it }
                .onFailure { lastError = it }
        }

        throw IllegalStateException("Failed to fetch update metadata from all proxy candidates", lastError)
    }

    suspend fun downloadApk(apkUrl: String, targetFile: File): File = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }

        var lastError: Throwable? = null
        for (candidateUrl in buildProxyCandidates(apkUrl)) {
            val request = Request.Builder()
                .url(candidateUrl)
                .header("User-Agent", buildUserAgent())
                .get()
                .build()

            val result = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Failed to download APK: HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty APK response body")
                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                targetFile
            }

            result.onSuccess { return@withContext it }
                .onFailure {
                    targetFile.delete()
                    lastError = it
                }
        }

        throw IllegalStateException("Failed to download APK from all proxy candidates", lastError)
    }

    suspend fun verifySha256(file: File, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        val expected = expectedSha256.trim().lowercase()
        if (expected.isBlank()) {
            return@withContext true
        }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            throw IllegalStateException("APK SHA256 verification failed")
        }

        true
    }

    fun hasNewVersion(latestVersionCode: Int, currentVersionCode: Int): Boolean {
        return latestVersionCode > currentVersionCode
    }

    private fun buildUserAgent(): String {
        return "com.android.chrome/131.0.6778.200 (Linux;Android ${Build.VERSION.RELEASE}) AndroidXMedia3/$MEDIA3_VERSION"
    }

    private companion object {
        private const val MEDIA3_VERSION = "1.6.0"
        val PROXY_PREFIXES = listOf(
            "https://edgeone.gh-proxy.org/",
            "https://ghfast.top/"
        )
    }
}
