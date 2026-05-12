package org.example

import org.example.AppLogger
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * Интерфейс для взаимодействия с клиентами
 */
interface ServerTransport {
    fun poll(): List<NetworkMessage>
    fun send(senderId: String, data: ByteArray)
    fun close()
    fun clientsMap(): ServerClientsMap<*>
}

object ServerTransportFactory {
    fun create(type: TransportType, port: Int): ServerTransport = when (type) {
        TransportType.TCP_STREAM ->
            ServerTcpStreamTransport(port, ServerClientsMap())
        TransportType.TCP_NIO ->
            ServerTcpNioTransport(port, ServerClientsMap())
        TransportType.UDP ->
            ServerUdpTransport(port, ServerClientsMap())
    }
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
 * Реализует ServerTransport с потоками ввода-вывода
 */
class ServerTcpStreamTransport(port: Int, val clientsMap: ServerClientsMap<Socket>) : ServerTransport {
    private val serverSocket = ServerSocket(port).apply { soTimeout = 50 }

    override fun poll(): List<NetworkMessage> {
        val messages = mutableListOf<NetworkMessage>()

        try {
            val newSocket = serverSocket.accept()
            newSocket.soTimeout = 50
            val id = clientsMap.addClient(newSocket)
            AppLogger.log("[$id] подключен")
        } catch (_: java.net.SocketTimeoutException) {}

        val dead = mutableListOf<String>()
        for ((id, socket) in clientsMap.allClients()) {
            try {
                val input = socket.getInputStream()
                if (input.available() == 0) continue

                val header = ByteArray(4)
                input.readNBytes(header, 0, 4)
                val len = ((header[0].toInt() and 0xFF) shl 24) or
                        ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                val payload = input.readNBytes(len)
                messages.add(NetworkMessage(id, payload))
            } catch (e: Exception) {
                dead.add(id)
            }
        }

        dead.forEach { id ->
            clientsMap.removeClient(id).also { it.close() }
            AppLogger.log("[$id] отключен")
        }
        return messages
    }

    override fun send(senderId: String, data: ByteArray) {
        clientsMap.allClients()[senderId]?.getOutputStream()?.let { out ->
            out.write(byteArrayOf(
                (data.size shr 24).toByte(), (data.size shr 16).toByte(),
                (data.size shr 8).toByte(), data.size.toByte()
            ))
            out.write(data)
            out.flush()
        }
    }

    override fun close() {
        clientsMap.allClients().values.forEach { it.close() }
        serverSocket.close()
    }

    override fun clientsMap(): ServerClientsMap<*> = clientsMap
}

/**
 * Реализует ServerTransport с сетевым каналом
 */
class ServerTcpNioTransport(port: Int, val clientsMap: ServerClientsMap<SocketChannel>) : ServerTransport {
    private val serverChannel = ServerSocketChannel.open().apply {
        bind(InetSocketAddress(port))
        configureBlocking(false)
    }

    override fun poll(): List<NetworkMessage> {
        val messages = mutableListOf<NetworkMessage>()

        serverChannel.accept()?.let { ch ->
            ch.configureBlocking(false)
            val id = clientsMap.addClient(ch)
            AppLogger.log("[$id] подключен")
        }

        val dead = mutableListOf<String>()
        for ((id, ch) in clientsMap.allClients()) {
            try {
                if (!ch.isOpen) { dead.add(id); continue }

                val header = ByteBuffer.allocate(4)
                while (header.hasRemaining() && ch.read(header) > 0) {}
                if (header.hasRemaining()) continue // ещё не пришло полностью

                header.flip()
                val len = header.int

                val payload = ByteBuffer.allocate(len)
                while (payload.hasRemaining() && ch.read(payload) > 0) {}
                if (payload.hasRemaining()) continue // частичный пакет

                payload.flip()
                val data = ByteArray(payload.remaining()).apply { payload.get(this) }
                messages.add(NetworkMessage(id, data))
            } catch (e: Exception) {
                dead.add(id)
            }
        }

        dead.forEach {
            id -> clientsMap.removeClient(id).also { it.close() }
            AppLogger.log("[$id] отключен")
        }
        return messages
    }

    override fun send(senderId: String, data: ByteArray) {
        clientsMap.allClients()[senderId]?.let { ch ->
            val buf = ByteBuffer.allocate(data.size + 4)
            buf.putInt(data.size)
            buf.put(data)
            buf.flip()
            while (buf.hasRemaining()) ch.write(buf)
        }
    }

    override fun close() {
        clientsMap.allClients().values.forEach { it.close() }
        serverChannel.close()
    }

    override fun clientsMap(): ServerClientsMap<*> = clientsMap
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
