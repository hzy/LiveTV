package dev.faraway.livetv.scanner

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Lightweight liveness probe. Joins a multicast group on (host, port) and
 * returns true as soon as any UDP packet arrives within [timeoutMs].
 *
 * Used by [ChannelScanner] to quickly filter out empty (IP, port) pairs
 * before paying the cost of [SdtNameResolver.resolve], which needs several
 * seconds of stream to capture an SDT section.
 */
internal object UdpLivenessProbe {

    fun isLive(host: String, port: Int, timeoutMs: Long): Boolean {
        val addr = try { InetAddress.getByName(host) } catch (_: Throwable) { return false }
        val sa = InetSocketAddress(addr, port)
        val ni = pickInterface()

        val sock = try { MulticastSocket(port) } catch (_: Throwable) { return false }
        sock.reuseAddress = true
        sock.receiveBufferSize = 64 * 1024
        sock.soTimeout = timeoutMs.toInt().coerceAtLeast(1)
        try {
            try {
                if (ni != null) sock.joinGroup(sa, ni) else sock.joinGroup(addr)
            } catch (_: Throwable) {
                return false
            }
            val buf = ByteArray(2048)
            val pkt = DatagramPacket(buf, buf.size)
            return try {
                sock.receive(pkt)
                pkt.length > 0
            } catch (_: Throwable) {
                false
            } finally {
                try { if (ni != null) sock.leaveGroup(sa, ni) else sock.leaveGroup(addr) } catch (_: Throwable) {}
            }
        } finally {
            sock.close()
        }
    }

    private fun pickInterface(): NetworkInterface? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().firstOrNull { ni ->
                ni.isUp && !ni.isLoopback && ni.supportsMulticast() &&
                    ni.inetAddresses.toList().any { !it.isLoopbackAddress && it.address.size == 4 }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
