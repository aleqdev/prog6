package org.example

import java.io.OutputStreamWriter
import java.util.Scanner

/**
 * Контекст подготовки команды на клиенте
 */
interface ClientCommandContext {
    fun output(): OutputStreamWriter
    fun scanner(): Scanner
    fun args(): List<String>
    fun history(): MutableList<String>
    fun shouldExit(): ShouldExitRef
    fun executionHistory(): MutableList<String>
    fun localCommands(): List<LocalClientCommand>
    fun networkCommands(): List<NetworkClientCommand>
}

data class ShouldExitRef(var value: Boolean)

/**
 * Локальная команда клиента
 */
interface LocalClientCommand {
    fun name(): String
    fun description(): String
    fun execute(context: ClientCommandContext)
}

/**
 * Сетевая команда
 */
interface NetworkClientCommand {
    fun name(): String
    fun description(): String
    fun prepare(context: ClientCommandContext): Command?
}
