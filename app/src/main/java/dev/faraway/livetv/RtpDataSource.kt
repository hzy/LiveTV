package dev.faraway.livetv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * A minimal DataSource that joins an RTP multicast group and yields raw
 * payload bytes (RTP header stripped) so ExoPlayer's TS extractor can parse
 * the MPEG-TS stream that IPTV providers wrap inside RTP.
 *
 * Supports URIs of the form rtp://group:port (the address from the URI is
 * used as the multicast group).
 */
class RtpDataSource(
    private val maxPacketSize: Int = 1500,
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = RtpDataSource()
    }

    private var socket: MulticastSocket? = null
    private var group: InetAddress? = null
    private var groupSockAddr: InetSocketAddress? = null
    private var networkInterface: NetworkInterface? = null
    private var uri: Uri? = null
    private var opened: Boolean = false

    private val packetBuffer = ByteArray(maxPacketSize)
    private val packet = DatagramPacket(packetBuffer, packetBuffer.size)
    private var payloadOffset: Int = 0
    private var payloadLength: Int = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val host = dataSpec.uri.host ?: throw IOException("rtp uri missing host")
        val port = dataSpec.uri.port.takeIf { it > 0 } ?: throw IOException("rtp uri missing port")
        transferInitializing(dataSpec)
        try {
            val addr = InetAddress.getByName(host)
            val sock = MulticastSocket(port)
            sock.reuseAddress = true
            // Slightly larger receive buffer helps avoid drops on bursty streams.
            sock.receiveBufferSize = 2 * 1024 * 1024
            sock.soTimeout = 8000
            val sa = InetSocketAddress(addr, port)
            // Bind the join to the active non-loopback IPv4 interface so it
            // works reliably on TVs where the default interface may differ.
            val ni = pickNetworkInterface()
            if (ni != null) sock.joinGroup(sa, ni) else sock.joinGroup(addr)
            socket = sock
            group = addr
            groupSockAddr = sa
            networkInterface = ni
            opened = true
            transferStarted(dataSpec)
            return C.LENGTH_UNSET.toLong()
        } catch (e: IOException) {
            closeQuietly()
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (payloadLength == 0) {
            val sock = socket ?: throw IOException("not opened")
            packet.length = packetBuffer.size
            sock.receive(packet)
            val received = packet.length
            if (received < 12) {
                // Not a valid RTP packet, skip.
                return 0
            }
            // RTP header parsing (RFC 3550). We trust IPTV streams to use the
            // standard 12-byte header with no CSRC and no extension; still
            // honor the flags so it's robust.
            val first = packetBuffer[0].toInt() and 0xFF
            val cc = first and 0x0F
            val extension = (first and 0x10) != 0
            val padding = (first and 0x20) != 0
            var headerLen = 12 + cc * 4
            if (extension && received >= headerLen + 4) {
                val extLenWords = ((packetBuffer[headerLen + 2].toInt() and 0xFF) shl 8) or
                    (packetBuffer[headerLen + 3].toInt() and 0xFF)
                headerLen += 4 + extLenWords * 4
            }
            var payloadEnd = received
            if (padding && received > headerLen) {
                val padLen = packetBuffer[received - 1].toInt() and 0xFF
                payloadEnd = (received - padLen).coerceAtLeast(headerLen)
            }
            payloadOffset = headerLen
            payloadLength = (payloadEnd - headerLen).coerceAtLeast(0)
            bytesTransferred(payloadLength)
            if (payloadLength == 0) return 0
        }
        val n = minOf(length, payloadLength)
        System.arraycopy(packetBuffer, payloadOffset, buffer, offset, n)
        payloadOffset += n
        payloadLength -= n
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeQuietly()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeQuietly() {
        try {
            val sock = socket
            val sa = groupSockAddr
            val ni = networkInterface
            if (sock != null && sa != null) {
                try {
                    if (ni != null) sock.leaveGroup(sa, ni) else sock.leaveGroup(group)
                } catch (_: Throwable) { }
            }
            sock?.close()
        } catch (_: Throwable) { }
        socket = null
        group = null
        groupSockAddr = null
        networkInterface = null
        payloadOffset = 0
        payloadLength = 0
    }

    private fun pickNetworkInterface(): NetworkInterface? {
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
