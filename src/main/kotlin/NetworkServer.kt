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
 * Класс для хранения клиентов
 */
class ServerClientsMap<SocketT> {
    private var nextId = 0
    private var clients = mutableMapOf<ClientId, SocketT>()

    fun addClient(socket: SocketT): ClientId {
        val id = "client_${nextId++}"
        clients[id] = socket
        return id
    }

    fun removeClient(id: ClientId): SocketT {
        val client = clients[id]!!
        clients = clients.filterNot { it.key == id }.toMutableMap()
        return client
    }

    fun allClients(): Map<ClientId, SocketT> = clients

    fun findIdByClient(client: SocketT): ClientId? {
        return allClients().entries.find { it.value == client }?.key
    }
}

/**
 * Реализует ServerTransport с датаграммами
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
 * Взаимодействует с клиентами на уровне команд
 */
class ServerCommandMessenger(
    private val transport: ServerTransport,
    private val mapper: ObjectMapper = NetworkMapper.mapper
) {
    fun sendResponse(id: ClientId, response: CommandResponse) {
        val json = mapper.writeValueAsString(response)
        AppLogger.log("Отправка результата: $json")
        transport.send(id, json.toByteArray(Charsets.UTF_8))
    }

    fun tryReceiveCommands(): List<Pair<ClientId, CommandRequest<*>>> {
        val rawMessages = transport.poll()
        val result: MutableList<Pair<ClientId, CommandRequest<*>>> = mutableListOf()

        rawMessages.forEach { message ->
            val json = String(message.data, Charsets.UTF_8)
            AppLogger.log("Получена команда: $json")
            val cmd = mapper.readValue(json, CommandRequest::class.java)
            result.add(message.senderId to cmd)
        }

        return result
    }
}