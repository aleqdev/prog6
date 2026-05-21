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

typealias ClientId = String
