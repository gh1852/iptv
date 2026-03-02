package com.jons.iptv.data

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

    private fun withGhFastProxy(url: String): String {
        val normalized = url.trim()
        return if (normalized.startsWith(GH_FAST_PREFIX)) normalized else "$GH_FAST_PREFIX$normalized"
    }

    suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(withGhFastProxy(metadataUrl))
            .header("User-Agent", "Mozilla/5.0 (Android) IPTV/1.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch update metadata: HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)

            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = withGhFastProxy(json.getString("apkUrl")),
                sha256 = json.optString("sha256", ""),
                changelog = json.optString("changelog", ""),
                force = json.optBoolean("force", false),
                minSupportedVersionCode = json.optInt("minSupportedVersionCode", 1)
            )
        }
    }

    suspend fun downloadApk(apkUrl: String, targetFile: File): File = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", "Mozilla/5.0 (Android) IPTV/1.0")
            .get()
            .build()

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

    private companion object {
        const val GH_FAST_PREFIX = "https://edgeone.gh-proxy.org/"
    }
}
