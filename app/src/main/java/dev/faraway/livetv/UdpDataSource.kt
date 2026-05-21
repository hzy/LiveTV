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
 * Plain UDP multicast DataSource (no header stripping). For udp:// URIs that
 * already carry MPEG-TS directly.
 */
class UdpDataSource : BaseDataSource(true) {

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = UdpDataSource()
    }

    private var socket: MulticastSocket? = null
    private var group: InetAddress? = null
    private var groupSockAddr: InetSocketAddress? = null
    private var networkInterface: NetworkInterface? = null
    private var uri: Uri? = null
    private var opened: Boolean = false

    private val buf = ByteArray(1500)
    private val packet = DatagramPacket(buf, buf.size)
    private var pos = 0
    private var len = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val host = dataSpec.uri.host ?: throw IOException("udp uri missing host")
        val port = dataSpec.uri.port.takeIf { it > 0 } ?: throw IOException("udp uri missing port")
        transferInitializing(dataSpec)
        try {
            val addr = InetAddress.getByName(host)
            val sock = MulticastSocket(port)
            sock.reuseAddress = true
            sock.receiveBufferSize = 2 * 1024 * 1024
            sock.soTimeout = 8000
            val sa = InetSocketAddress(addr, port)
            val ni = NetworkInterface.getNetworkInterfaces().toList().firstOrNull {
                it.isUp && !it.isLoopback && it.supportsMulticast()
            }
            if (ni != null) sock.joinGroup(sa, ni) else sock.joinGroup(addr)
            socket = sock
            group = addr
            groupSockAddr = sa
            networkInterface = ni
            opened = true
            transferStarted(dataSpec)
            return C.LENGTH_UNSET.toLong()
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (len == 0) {
            val s = socket ?: throw IOException("not opened")
            packet.length = buf.size
            s.receive(packet)
            pos = 0
            len = packet.length
            bytesTransferred(len)
        }
        val n = minOf(length, len)
        System.arraycopy(buf, pos, buffer, offset, n)
        pos += n
        len -= n
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            val s = socket
            val sa = groupSockAddr
            val ni = networkInterface
            if (s != null && sa != null) {
                try {
                    if (ni != null) s.leaveGroup(sa, ni) else s.leaveGroup(group)
                } catch (_: Throwable) { }
            }
            s?.close()
        } catch (_: Throwable) { }
        socket = null
        group = null
        groupSockAddr = null
        networkInterface = null
        pos = 0
        len = 0
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
