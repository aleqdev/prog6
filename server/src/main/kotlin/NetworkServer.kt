package org.example

import tools.jackson.databind.ObjectMapper
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * Интерфейс для взаимодействия с клиентами
 */
interface ServerTransport {
    fun poll(): List<NetworkMessage>
    fun send(senderId: String, data: ByteArray)
    fun close()
    fun clientsMap(): ServerClientsMap<*>
}

/**
 * Реализует ServerTransport с датаграммами в неблокирующем режиме (как требует лаба).
 */
class ServerUdpTransport(port: Int, val clientsMap: ServerClientsMap<SocketAddress>) : ServerTransport {
    private val channel = DatagramChannel.open().apply {
        configureBlocking(false)
        bind(InetSocketAddress(port))
    }

    override fun poll(): List<NetworkMessage> {
        val messages = mutableListOf<NetworkMessage>()
        val buf = ByteBuffer.allocate(65535)

        while (true) {
            val sender = try { channel.receive(buf) } catch (_: Exception) { null }
            if (sender == null) break

            buf.flip()
            val data = ByteArray(buf.remaining()).apply { buf.get(this) }
            buf.clear()

            val id = clientsMap.findIdByClient(sender) ?: run {
                val id = clientsMap.addClient(sender)
                AppLogger.log("[$id] подключен")
                id
            }
            messages.add(NetworkMessage(id, data))
        }
        return messages
    }

    override fun send(senderId: String, data: ByteArray) {
        clientsMap.allClients()[senderId]?.let { addr ->
            channel.send(ByteBuffer.wrap(data), addr)
        }
    }

    override fun close() {
        channel.close()
    }

    override fun clientsMap(): ServerClientsMap<*> = clientsMap
}

/**
 * Результат разбора входящего сообщения.
 */
sealed class IncomingMessage {
    data class Parsed(val senderId: ClientId, val command: Command) : IncomingMessage()
    data class Malformed(val senderId: ClientId, val reason: String) : IncomingMessage()
}

/**
 * Взаимодействует с клиентами на уровне команд.
 */
class ServerCommandMessenger(
    private val transport: ServerTransport,
    private val mapper: ObjectMapper = NetworkMapper.mapper
) {
    fun sendResponse(id: ClientId, response: CommandResponse) {
        val json = mapper.writeValueAsString(response)
        AppLogger.log("[$id] отправка ответа: $json")
        transport.send(id, json.toByteArray(Charsets.UTF_8))
    }

    fun tryReceiveCommands(): List<IncomingMessage> {
        val rawMessages = transport.poll()
        val result: MutableList<IncomingMessage> = mutableListOf()

        rawMessages.forEach { message ->
            val json = String(message.data, Charsets.UTF_8)
            AppLogger.log("[${message.senderId}] получен запрос: $json")
            val parsed = try {
                IncomingMessage.Parsed(message.senderId, mapper.readValue(json, Command::class.java))
            } catch (e: Exception) {
                IncomingMessage.Malformed(message.senderId, e.message ?: "не удалось распознать команду")
            }
            result.add(parsed)
        }

        return result
    }
}
