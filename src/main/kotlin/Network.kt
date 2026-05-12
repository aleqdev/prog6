package org.example

import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule
import java.io.InputStream
import java.io.OutputStream
import java.lang.AutoCloseable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * Сереализует команды и их <DT>
 */
object NetworkMapper {
    private val typeValidator = BasicPolymorphicTypeValidator.builder()
        .allowIfBaseType("org.example.")
        .allowIfSubType("org.example.")
        .allowIfBaseType("java.lang.")
        .allowIfSubType("java.lang.")
        .build()

    val mapper: ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
        .polymorphicTypeValidator(typeValidator)
        .build()
}

/**
 * Клиент отправляет экземпляры данного класса серверу
 */
data class CommandRequest<CD>(
    val command: Command<CD>,
    @field:JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        property = "@data"
    )
    val data: Any?
)

/**
 * После выполнения команды сервер возвращает результат - экземпляр данного класса
 */
data class CommandResponse (
    val output: String
)

/**
 * Экзампляры этого класса сериализуются через ServerCommandMessenger/ClientCommandMessenger
 */
data class NetworkMessage(val senderId: ClientId, val data: ByteArray)

enum class TransportType { TCP_STREAM, TCP_NIO, UDP }

typealias ClientId = String

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

/**
 * Взаимодействует с сервером на уровне команд
 */
class ClientCommandMessenger(
    private val transport: ClientTransport,
    private val mapper: ObjectMapper = NetworkMapper.mapper
): AutoCloseable {
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
