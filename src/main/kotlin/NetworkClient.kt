package org.example

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel


/**
 * Интерфейс для взаимодействия с сервером
 */
interface ClientTransport: AutoCloseable {
    fun send(bytes: ByteArray)
    fun tryReceive(): ByteArray?
}

object ClientTransportFactory {
    fun create(type: TransportType, host: String, port: Int): ClientTransport = when (type) {
        TransportType.TCP_STREAM -> ClientTcpStreamTransport(host, port)
        TransportType.TCP_NIO    -> ClientTcpNioTransport(host, port)
        TransportType.UDP        -> ClientUdpTransport(host, port)
    }
}

/**
 * Реализует ClientTransport с потоками ввода-вывода
 */
class ClientTcpStreamTransport(host: String, port: Int) : ClientTransport {
    private var socket: Socket = Socket(host, port).apply { soTimeout = 2000 }
    private var input: InputStream = socket.getInputStream()
    private var output: OutputStream = socket.getOutputStream()

    override fun send(bytes: ByteArray) {
        val len = bytes.size
        output.write(byteArrayOf(
            (len shr 24).toByte(), (len shr 16).toByte(),
            (len shr 8).toByte(), len.toByte()
        ))
        output.write(bytes)
        output.flush()
    }

    override fun tryReceive(): ByteArray? {
        return try {
            if (input.available() == 0) return null
            val header = ByteArray(4)
            input.readNBytes(header, 0, 4)
            val len = ((header[0].toInt() and 0xFF) shl 24) or
                    ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
            input.readNBytes(len)
        } catch (e: java.net.SocketTimeoutException) { null }
        catch (e: Exception) { null }
    }

    override fun close() {
        input.close()
        output.close()
        socket.close()
    }
}

/**
 * Реализует ClientTransport с сетевым каналом
 */
class ClientTcpNioTransport(host: String, port: Int) : ClientTransport {
    private var channel: SocketChannel = SocketChannel.open().apply {
        configureBlocking(false)
        connect(InetSocketAddress(host, port))
        while (!finishConnect()) Thread.sleep(10)
    }

    override fun send(bytes: ByteArray) {
        val buf = ByteBuffer.allocate(bytes.size + 4)
        buf.putInt(bytes.size)
        buf.put(bytes)
        buf.flip()
        while (buf.hasRemaining()) channel.write(buf)
    }

    override fun tryReceive(): ByteArray? {
        return try {
            val header = ByteBuffer.allocate(4)
            while (header.hasRemaining() && channel.read(header) > 0) {}
            if (header.hasRemaining()) return null // неполный заголовок
            header.flip()
            val len = header.int
            val payload = ByteBuffer.allocate(len)
            while (payload.hasRemaining() && channel.read(payload) > 0) {}
            if (payload.hasRemaining()) return null // неполный пакет
            payload.flip()
            ByteArray(payload.remaining()).apply { payload.get(this) }
        } catch (_: Exception) { null }
    }

    override fun close() {
        channel.close()
    }
}

/**
 * Реализует ClientTransport с датаграммами
 */
class ClientUdpTransport(host: String, port: Int) : ClientTransport {
    private var channel: DatagramChannel = DatagramChannel.open().apply { configureBlocking(false) }
    private var serverAddr: InetSocketAddress = InetSocketAddress(host, port)

    override fun send(bytes: ByteArray) {
        channel.send(ByteBuffer.wrap(bytes), serverAddr)
    }

    override fun tryReceive(): ByteArray? {
        return try {
            val buf = ByteBuffer.allocate(65535)
            channel.receive(buf) ?: return null
            buf.flip()
            ByteArray(buf.remaining()).apply { buf.get(this) }
        } catch (_: Exception) { null }
    }

    override fun close() {
        channel.close()
    }
}
