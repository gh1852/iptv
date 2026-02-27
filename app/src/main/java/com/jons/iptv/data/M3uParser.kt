package com.jons.iptv.data

private data class ExtInfMeta(
    val name: String,
    val category: String,
    val logoUrl: String?
)

object M3uParser {
    private val attrRegex = Regex("([a-zA-Z0-9\\-]+)=\"([^\"]*)\"")

    fun parse(input: String): List<Channel> {
        val merged = linkedMapOf<String, MutableChannel>()
        var pendingMeta: ExtInfMeta? = null
        var currentCategory = "Other"

        input.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingMeta = parseExtInf(line)
                }

                line.startsWith("#") -> Unit

                line.endsWith(",#genre#", ignoreCase = true) -> {
                    val category = line.substringBefore(",#genre#").trim()
                    currentCategory = category.ifBlank { "Other" }
                    pendingMeta = null
                }

                pendingMeta != null -> {
                    val meta = pendingMeta ?: return@forEach
                    appendChannel(
                        merged = merged,
                        name = meta.name,
                        category = meta.category,
                        logoUrl = meta.logoUrl,
                        streamUrl = line
                    )
                    pendingMeta = null
                }

                else -> {
                    parsePlainEntry(line, currentCategory)?.let { entry ->
                        appendChannel(
                            merged = merged,
                            name = entry.name,
                            category = entry.category,
                            logoUrl = null,
                            streamUrl = entry.url
                        )
                    }
                }
            }
        }

        return merged.values
            .filter { it.streamUrls.isNotEmpty() }
            .map {
                Channel(
                    name = it.name,
                    category = it.category,
                    logoUrl = it.logoUrl,
                    streamUrls = it.streamUrls
                        .sortedByDescending(::streamPriority)
                )
            }
    }

    private fun appendChannel(
        merged: LinkedHashMap<String, MutableChannel>,
        name: String,
        category: String,
        logoUrl: String?,
        streamUrl: String
    ) {
        val normalizedName = name.trim().ifBlank { "Unknown Channel" }
        val normalizedCategory = category.trim().ifBlank { "Other" }
        val normalizedUrl = streamUrl.trim()
        if (normalizedUrl.isEmpty()) return

        val key = "$normalizedCategory|$normalizedName"
        val channel = merged.getOrPut(key) {
            MutableChannel(
                name = normalizedName,
                category = normalizedCategory,
                logoUrl = logoUrl,
                streamUrls = mutableListOf()
            )
        }

        if (channel.logoUrl.isNullOrBlank() && !logoUrl.isNullOrBlank()) {
            channel.logoUrl = logoUrl
        }

        if (!channel.streamUrls.contains(normalizedUrl)) {
            channel.streamUrls.add(normalizedUrl)
        }
    }

    private fun parsePlainEntry(line: String, currentCategory: String): PlainEntry? {
        val commaIndex = line.indexOf(',')
        if (commaIndex <= 0 || commaIndex >= line.lastIndex) return null

        val name = line.substring(0, commaIndex).trim()
        val url = line.substring(commaIndex + 1).trim()
        if (name.isEmpty() || url.isEmpty()) return null

        return PlainEntry(
            name = name,
            category = currentCategory.ifBlank { "Other" },
            url = url
        )
    }

    private fun parseExtInf(line: String): ExtInfMeta {
        val attrs = attrRegex.findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

        val name = line.substringAfter(',', attrs["tvg-name"] ?: "Unknown Channel").trim()
            .ifBlank { attrs["tvg-name"] ?: "Unknown Channel" }

        val group = attrs["group-title"].orEmpty().ifBlank { "Other" }
        val logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }

        return ExtInfMeta(name = name, category = group, logoUrl = logo)
    }

    private fun streamPriority(url: String): Int {
        val normalized = url.lowercase()
        var score = 0

        if (normalized.startsWith("https://")) score += 4
        if (normalized.startsWith("http://")) score += 2

        if (normalized.contains(".m3u8")) score += 5
        if (normalized.contains(".mpd")) score += 4
        if (normalized.contains(".ts") || normalized.contains(".mp4")) score += 2

        if (normalized.contains("udp://") || normalized.contains("rtp://")) score -= 3
        if (normalized.contains("localhost") || normalized.contains("127.0.0.1")) score -= 5

        return score
    }

    private data class PlainEntry(
        val name: String,
        val category: String,
        val url: String
    )

    private data class MutableChannel(
        val name: String,
        val category: String,
        var logoUrl: String?,
        val streamUrls: MutableList<String>
    )
}
