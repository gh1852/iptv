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

        input.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingMeta = parseExtInf(line)
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val meta = pendingMeta ?: return@forEach
                    val key = "${meta.category}|${meta.name}"
                    val channel = merged.getOrPut(key) {
                        MutableChannel(
                            name = meta.name,
                            category = meta.category,
                            logoUrl = meta.logoUrl,
                            streamUrls = mutableListOf()
                        )
                    }
                    if (channel.logoUrl.isNullOrBlank() && !meta.logoUrl.isNullOrBlank()) {
                        channel.logoUrl = meta.logoUrl
                    }
                    if (!channel.streamUrls.contains(line)) {
                        channel.streamUrls.add(line)
                    }
                    pendingMeta = null
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
                    streamUrls = it.streamUrls.toList()
                )
            }
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

    private data class MutableChannel(
        val name: String,
        val category: String,
        var logoUrl: String?,
        val streamUrls: MutableList<String>
    )
}
