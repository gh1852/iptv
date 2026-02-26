package com.jons.iptv.data

data class Channel(
    val name: String,
    val category: String,
    val logoUrl: String?,
    val streamUrls: List<String>
)
