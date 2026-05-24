package dev.faraway.livetv.scanner

import dev.faraway.livetv.Channel

/**
 * A channel discovered by [ChannelScanner].
 *
 * [name] is resolved by, in order:
 *   1. SDT service descriptor name (if present and non-empty)
 *   2. Built-in [dev.faraway.livetv.ChannelList] reverse-lookup by url
 *   3. A placeholder "未知 ip:port"
 */
data class DiscoveredChannel(
    val ip: String,
    val port: Int,
    val sdtName: String,
    val sdtProvider: String,
    val builtinName: String?,
    val builtinCategory: String?,
) {
    val url: String get() = "rtp://$ip:$port"

    val displayName: String get() = when {
        sdtName.isNotBlank() -> sdtName
        builtinName != null -> builtinName
        else -> "未知 $ip:$port"
    }

    val category: String get() = builtinCategory ?: "扫描"

    val source: String get() = when {
        sdtName.isNotBlank() -> "SDT"
        builtinName != null -> "内置"
        else -> "未知"
    }

    fun toChannel(id: Int, number: String): Channel = Channel(
        id = id,
        number = number,
        name = displayName,
        category = category,
        url = url,
    )
}
