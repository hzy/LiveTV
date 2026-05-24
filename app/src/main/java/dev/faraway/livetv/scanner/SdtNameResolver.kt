package dev.faraway.livetv.scanner

import android.util.Log
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.nio.charset.Charset

/**
 * Pulls the DVB Service Description Table (SDT) from an RTP/UDP multicast
 * stream and extracts the service name embedded in descriptor 0x48.
 *
 * Many providers leave name fields empty for repackaged channels (e.g. CCTV
 * on Beijing Unicom IPTV), so callers must treat a null/empty result as a
 * miss and fall back to other naming strategies.
 */
object SdtNameResolver {

    private const val TAG = "SdtNameResolver"
    private const val TS_SIZE = 188
    private const val SYNC: Byte = 0x47
    private const val PID_SDT = 0x0011

    data class Result(val provider: String, val name: String) {
        val isUseful: Boolean get() = name.isNotBlank()
    }

    /**
     * Listens to [host]:[port] for at most [timeoutMs] and returns the first
     * non-empty service name found, or null if none. Safe to call on a worker
     * thread — does blocking I/O.
     */
    fun resolve(host: String, port: Int, isRtp: Boolean, timeoutMs: Long): Result? {
        val addr = try { InetAddress.getByName(host) } catch (_: Throwable) { return null }
        val sa = InetSocketAddress(addr, port)
        val ni = pickInterface()

        val sock = try { MulticastSocket(port) } catch (_: Throwable) { return null }
        sock.reuseAddress = true
        sock.receiveBufferSize = 1024 * 1024
        sock.soTimeout = 500
        try {
            if (ni != null) sock.joinGroup(sa, ni) else sock.joinGroup(addr)
        } catch (_: Throwable) {
            sock.close()
            return null
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(4096)
        val pkt = DatagramPacket(buf, buf.size)
        var sectionBuf: ByteArray? = null
        var sectionPos = 0
        var sectionLen = 0
        var best: Result? = null

        try {
            outer@ while (System.currentTimeMillis() < deadline) {
                pkt.length = buf.size
                try {
                    sock.receive(pkt)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (_: Throwable) {
                    break
                }
                var off = 0
                var len = pkt.length
                if (isRtp) {
                    if (len < 12) continue
                    val b0 = buf[0].toInt() and 0xFF
                    val cc = b0 and 0x0F
                    val ext = (b0 and 0x10) != 0
                    var hdr = 12 + cc * 4
                    if (ext && len >= hdr + 4) {
                        val extLen = ((buf[hdr + 2].toInt() and 0xFF) shl 8) or
                            (buf[hdr + 3].toInt() and 0xFF)
                        hdr += 4 + extLen * 4
                    }
                    off = hdr
                    len = pkt.length - hdr
                }
                var p = off
                while (p + TS_SIZE <= off + len) {
                    if (buf[p] != SYNC) { p++; continue }
                    val b1 = buf[p + 1].toInt() and 0xFF
                    val b2 = buf[p + 2].toInt() and 0xFF
                    val b3 = buf[p + 3].toInt() and 0xFF
                    val pid = ((b1 and 0x1F) shl 8) or b2
                    val payloadStart = (b1 and 0x40) != 0
                    val afc = (b3 shr 4) and 0x03
                    val tsBase = p
                    p += TS_SIZE
                    if (pid != PID_SDT) continue
                    if ((afc and 0x01) == 0) continue
                    var dataStart = tsBase + 4
                    if ((afc and 0x02) != 0) {
                        val afLen = buf[tsBase + 4].toInt() and 0xFF
                        dataStart = tsBase + 5 + afLen
                    }
                    val tsEnd = tsBase + TS_SIZE
                    if (dataStart >= tsEnd) continue
                    if (payloadStart) {
                        val ptr = buf[dataStart].toInt() and 0xFF
                        val secStart = dataStart + 1 + ptr
                        if (secStart >= tsEnd) continue
                        val tableId = buf[secStart].toInt() and 0xFF
                        if (tableId != 0x42) continue
                        if (secStart + 3 > tsEnd) continue
                        val sl = ((buf[secStart + 1].toInt() and 0x0F) shl 8) or
                            (buf[secStart + 2].toInt() and 0xFF)
                        val total = sl + 3
                        val sb = ByteArray(total)
                        val avail = tsEnd - secStart
                        val copy = minOf(avail, total)
                        System.arraycopy(buf, secStart, sb, 0, copy)
                        sectionBuf = sb
                        sectionPos = copy
                        sectionLen = total
                        if (sectionPos >= sectionLen) {
                            val r = extractName(sb)
                            if (r != null) {
                                best = best.preferUseful(r)
                                if (best?.isUseful == true) break@outer
                            }
                            sectionBuf = null
                        }
                    } else {
                        val sb = sectionBuf ?: continue
                        val avail = tsEnd - dataStart
                        val need = sectionLen - sectionPos
                        val copy = minOf(avail, need)
                        if (copy > 0) {
                            System.arraycopy(buf, dataStart, sb, sectionPos, copy)
                            sectionPos += copy
                        }
                        if (sectionPos >= sectionLen) {
                            val r = extractName(sb)
                            if (r != null) {
                                best = best.preferUseful(r)
                                if (best?.isUseful == true) break@outer
                            }
                            sectionBuf = null
                        }
                    }
                }
            }
        } finally {
            try { if (ni != null) sock.leaveGroup(sa, ni) else sock.leaveGroup(addr) } catch (_: Throwable) {}
            sock.close()
        }
        return best
    }

    private fun Result?.preferUseful(other: Result): Result {
        return if (this == null || (!isUseful && other.isUseful)) other else this
    }

    private fun extractName(section: ByteArray): Result? {
        if (section.size < 15) return null
        var pos = 11
        val end = section.size - 4
        while (pos + 5 <= end) {
            val descLen = ((section[pos + 3].toInt() and 0x0F) shl 8) or
                (section[pos + 4].toInt() and 0xFF)
            val dStart = pos + 5
            val dEnd = dStart + descLen
            if (dEnd > end) break
            var dp = dStart
            while (dp + 2 <= dEnd) {
                val tag = section[dp].toInt() and 0xFF
                val len = section[dp + 1].toInt() and 0xFF
                if (dp + 2 + len > dEnd) break
                if (tag == 0x48 && len >= 3) {
                    val pLen = section[dp + 3].toInt() and 0xFF
                    val pStart = dp + 4
                    val nLenIdx = pStart + pLen
                    if (nLenIdx < dp + 2 + len) {
                        val nLen = section[nLenIdx].toInt() and 0xFF
                        val nStart = nLenIdx + 1
                        if (nStart + nLen <= dp + 2 + len) {
                            val provider = decodeDvbString(section, pStart, pLen)
                            val name = decodeDvbString(section, nStart, nLen)
                            return Result(provider, name)
                        }
                    }
                }
                dp += 2 + len
            }
            pos = dEnd
        }
        return null
    }

    private fun decodeDvbString(data: ByteArray, off: Int, len: Int): String {
        if (len <= 0) return ""
        val first = data[off].toInt() and 0xFF
        val (charset, skip) = when {
            first == 0x11 -> Charset.forName("UTF-16BE") to 1
            first == 0x13 -> Charset.forName("GB2312") to 1
            first == 0x14 -> Charset.forName("Big5") to 1
            first == 0x15 -> Charsets.UTF_8 to 1
            first < 0x20 -> Charset.forName("GB18030") to 1
            else -> Charset.forName("GB18030") to 0
        }
        return runCatching {
            String(data, off + skip, len - skip, charset)
                .trim { it.code < 0x20 }
        }.getOrElse { "" }
    }

    private fun pickInterface(): NetworkInterface? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().firstOrNull { ni ->
                ni.isUp && !ni.isLoopback && ni.supportsMulticast() &&
                    ni.inetAddresses.toList().any { !it.isLoopbackAddress && it.address.size == 4 }
            }
        } catch (_: Throwable) {
            null.also { Log.w(TAG, "pickInterface failed") }
        }
    }
}
