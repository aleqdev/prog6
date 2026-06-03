package org.example

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Сериализатор сетевых сообщений.
 *
 * Полиморфная типизация работает ТОЛЬКО через явный whitelist подклассов
 * Command (@JsonSubTypes). Default-typing намеренно не активирован, чтобы
 * никакой класс с classpath нельзя было инстанцировать через @class.
 */
object NetworkMapper {
    val mapper: ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()
}

/**
 * Ответ сервера на команду — текст для вывода клиенту.
 */
data class CommandResponse(val output: String)

/**
 * Сырое сообщение от/к клиенту на уровне транспорта.
 */
data class NetworkMessage(val senderId: ClientId, val data: ByteArray)

typealias ClientId = String
