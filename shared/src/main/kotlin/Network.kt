package org.example

import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule

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

/**
 * Хранилище подключённых клиентов на стороне сервера.
 * Используется в ServerCommandContext, поэтому живёт в shared.
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
