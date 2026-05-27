package org.example

import tools.jackson.databind.ObjectMapper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException


/**
 * Интерфейс для взаимодействия с сервером
 */
interface ClientTransport: AutoCloseable {
    fun send(bytes: ByteArray)
    fun tryReceive(): ByteArray?
}

/**
 * Реализует ClientTransport с датаграммами
 */
class ClientUdpTransport(host: String, port: Int) : ClientTransport {
    private val socket = DatagramSocket()
    private val serverAddr = InetSocketAddress(host, port)

    init {
        socket.soTimeout = 10
    }

    override fun send(bytes: ByteArray) {
        val packet = DatagramPacket(bytes, bytes.size, serverAddr)
        socket.send(packet)
    }

    override fun tryReceive(): ByteArray? {
        return try {
            val buf = ByteArray(65535)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            buf.copyOf(packet.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        socket.close()
    }
}

/**
 * Взаимодействует с сервером на уровне команд
 */
class ClientCommandMessenger(
    private val transport: ClientTransport,
    private val mapper: ObjectMapper = NetworkMapper.mapper
): java.lang.AutoCloseable {
    fun sendCommand(command: Command<*>, data: Any?) {
        val request = CommandRequest(command, data)
        val json = mapper.writeValueAsString(request)
        transport.send(json.toByteArray(Charsets.UTF_8))
    }

    fun tryReceiveResponse(): CommandResponse? {
        val recv = transport.tryReceive() ?: return null

        val json = String(recv, Charsets.UTF_8)

        return mapper.readValue(json, CommandResponse::class.java)
    }

    override fun close() {
        sendCommand(
            DisconnectClientCommand(),
            Unit,
        )
    }
}
