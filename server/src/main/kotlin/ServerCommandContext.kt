package org.example

import java.io.OutputStreamWriter

/**
 * Контекст исполнения команды на сервере
 */
interface ServerCommandContext {
    fun db(): Database<*>
    fun output(): OutputStreamWriter
    fun clientsMap(): ServerClientsMap<*>
    fun senderId(): ClientId?
    fun dbFilename(): String
}
