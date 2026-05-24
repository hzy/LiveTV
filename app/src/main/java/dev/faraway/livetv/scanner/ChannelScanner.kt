package dev.faraway.livetv.scanner

import android.util.Log
import dev.faraway.livetv.Channel
import dev.faraway.livetv.ChannelList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Scans a multicast IP range for live RTP streams and reports them as a
 * [Flow] of [ScanEvent]s.
 *
 * Two-stage strategy:
 *   1. liveness: short ([detectTimeoutMs]) UDP receive to filter pairs that
 *      have any traffic.
 *   2. naming:   longer ([nameTimeoutMs]) capture to extract the SDT service
 *      name; on miss, falls back to the bundled [ChannelList] or a
 *      placeholder.
 *
 * Concurrency is bounded by [parallelism] so we don't exhaust sockets on TVs.
 */
class ChannelScanner(
    private val ipStart: String = "239.3.1.1",
    private val ipEnd: String = "239.3.1.254",
    private val ports: IntArray = ScanPresets.QUICK_PORTS,
    private val parallelism: Int = 16,
    private val detectTimeoutMs: Long = 1500L,
    private val nameTimeoutMs: Long = 3500L,
) {

    sealed interface ScanEvent {
        data class Progress(val checked: Int, val total: Int, val current: String) : ScanEvent
        data class Found(val channel: DiscoveredChannel) : ScanEvent
        data class Done(val channels: List<DiscoveredChannel>) : ScanEvent
        data class Error(val message: String) : ScanEvent
    }

    private companion object {
        const val TAG = "ChannelScanner"
    }

    fun scan(): Flow<ScanEvent> = channelFlow {
        val startUint = ipToUint(ipStart) ?: run {
            send(ScanEvent.Error("起始IP无效: $ipStart")); return@channelFlow
        }
        val endUint = ipToUint(ipEnd) ?: run {
            send(ScanEvent.Error("结束IP无效: $ipEnd")); return@channelFlow
        }
        if (startUint > endUint) {
            send(ScanEvent.Error("起始IP必须 ≤ 结束IP")); return@channelFlow
        }

        val builtinIndex = ChannelList.channels.associateBy { it.url }
        val targets = buildList {
            for (ip in startUint..endUint) {
                val ipStr = uintToIp(ip)
                for (p in ports) add(ipStr to p)
            }
        }
        val total = targets.size
        val found = mutableListOf<DiscoveredChannel>()
        val semaphore = Semaphore(parallelism)
        var checked = 0

        coroutineScope {
            for ((ip, port) in targets) {
                semaphore.acquire()
                launch(Dispatchers.IO) {
                    try {
                        val live = UdpLivenessProbe.isLive(ip, port, detectTimeoutMs)
                        synchronized(found) { checked++ }
                        send(ScanEvent.Progress(checked, total, "$ip:$port"))
                        if (!live) return@launch

                        val sdt = SdtNameResolver.resolve(ip, port, isRtp = true, timeoutMs = nameTimeoutMs)
                        val builtin = builtinIndex["rtp://$ip:$port"]
                        val ch = DiscoveredChannel(
                            ip = ip,
                            port = port,
                            sdtName = sdt?.name?.takeIf { it.isNotBlank() } ?: "",
                            sdtProvider = sdt?.provider ?: "",
                            builtinName = builtin?.name,
                            builtinCategory = builtin?.category,
                        )
                        synchronized(found) { found += ch }
                        send(ScanEvent.Found(ch))
                        Log.i(TAG, "FOUND $ip:$port → ${ch.displayName} (${ch.source})")
                    } catch (t: Throwable) {
                        Log.w(TAG, "probe $ip:$port failed", t)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }

        val sorted = withContext(Dispatchers.Default) {
            found.sortedWith(compareBy({ ipToUint(it.ip) ?: 0L }, { it.port }))
        }
        send(ScanEvent.Done(sorted))
    }.flowOn(Dispatchers.IO)

    private fun ipToUint(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        var v = 0L
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    private fun uintToIp(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
}
