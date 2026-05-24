package dev.faraway.livetv.scanner

import dev.faraway.livetv.ChannelList

/**
 * Pre-built port sets for [ChannelScanner].
 *
 * QUICK    — top-frequency ports observed in [ChannelList]; ~3 min over the
 *            default 239.3.1.1-254 range with parallelism=16.
 * FULL     — every distinct port that appears in [ChannelList]; ~20 min.
 */
object ScanPresets {

    val QUICK_PORTS: IntArray = intArrayOf(8001, 8000, 4120, 2000, 1234, 8092, 3001, 8108)

    val FULL_PORTS: IntArray by lazy {
        ChannelList.channels
            .mapNotNull { ch ->
                val schemeStripped = ch.url.removePrefix("rtp://").removePrefix("udp://")
                schemeStripped.substringAfter(':', "").toIntOrNull()
            }
            .toSortedSet()
            .toIntArray()
    }

    enum class Preset(val label: String, val description: String, val ports: () -> IntArray) {
        QUICK("快速搜台", "约 3 分钟 · 高频端口", { QUICK_PORTS }),
        FULL("全量搜台", "约 20 分钟 · 所有端口", { FULL_PORTS }),
    }
}
