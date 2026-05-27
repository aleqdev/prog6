package org.example

import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.SocketTimeoutException
import java.util.Scanner

fun startClient() {
    val commands = allClientCommands()
    val writer = OutputStreamWriter(System.`out`, "UTF-8")
    val history: MutableList<String> = mutableListOf()

    clientMainLoop(
        commands,
        writer,
        history
    )
}

fun clientMainLoop(
    commands: List<Command<*>>,
    output: OutputStreamWriter,
    history: MutableList<String> = mutableListOf()
) {
    val shouldExit = ShouldExitRef(false)

    val reader = InputStreamReader(System.`in`, "UTF-8")
    val readerScanner = Scanner(reader)
    val asyncScanner = object : AsyncCommandScanner {
        override fun hasNextLine(): Boolean = readerScanner.hasNextLine()
        override fun nextLine(): String = readerScanner.nextLine()
        override fun spinUntilInputAvailable() {}
        override fun inner(): Scanner = readerScanner
    }

    while (!shouldExit.value) {
        ClientUdpTransport("localhost", 8323).use { transport ->
            ClientCommandMessenger(transport).use { messenger ->
                processClientMainLoop(
                    shouldExit,
                    output,
                    asyncScanner,
                    commands,
                    history,
                    messenger
                )
            }
        }
    }
}

fun processClientMainLoop(
    shouldExit: ShouldExitRef,
    output: OutputStreamWriter,
    asyncScanner: AsyncCommandScanner,
    commands: List<Command<*>>,
    history: MutableList<String>,
    messenger: ClientCommandMessenger
) {
    while (!shouldExit.value) {
        try {
            output.flush()
            print("> ")

            if (!asyncScanner.hasNextLine()) {
                break
            }

            val line = asyncScanner.nextLine().trim()
            if (line.isEmpty()) continue

            val parts = line.split(Regex("\\s+"))
            val name = parts.first()
            val args = parts.drop(1)

            val prepareCtx = object : CommandPrepareContext {
                override fun registry(): CommandRegistry = object : CommandRegistry {
                    override fun commands(): List<Command<*>> = commands
                }

                override fun scanner(): AsyncCommandScanner = asyncScanner
                override fun output(): OutputStreamWriter = output
                override fun args(): List<String> = args
                override fun executionHistory(): MutableList<String> = mutableListOf()
            }

            val ctx = object : LocalCommandContext {
                override fun registry(): CommandRegistry = object : CommandRegistry {
                    override fun commands(): List<Command<*>> = commands
                }

                override fun output(): OutputStreamWriter = output
                override fun shouldExit(): ShouldExitRef = shouldExit
                override fun history(): MutableList<String> = history
            }

            val command = commands.find { it.name() == name }
            if (command != null) {
                when (command) {
                    is LocalCommand<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val typedCommand = command as LocalCommand<Any?>
                        typedCommand.prepare(prepareCtx)?.let { data ->
                            typedCommand.processLocal(ctx, data)
                        }
                    }

                    is ServerCommand -> {
                        @Suppress("UNCHECKED_CAST")
                        val typedCommand = command as ServerCommand<Any?>
                        typedCommand.prepare(prepareCtx)?.let { data ->
                            messenger.sendCommand(typedCommand, data)

                            var gotResponse = false
                            repeat(60) {
                                if (!gotResponse) {
                                    messenger.tryReceiveResponse()?.let { resp ->
                                        print(resp.output)
                                        gotResponse = true
                                        return@repeat
                                    }
                                    Thread.sleep(50)
                                }
                            }

                            if (!gotResponse) throw SocketTimeoutException("таймаут сервера")
                        }
                    }

                    else -> throw IllegalArgumentException(command::class.simpleName)
                }
                history.add(name)
                if (history.size > 8) history.removeAt(0)
            } else {
                output.write("Неизвестная команда: $name. Введите 'help'\n")
            }
        } catch (e: Exception) {
            println("Ошибка: ${e.message}")
            Thread.sleep(50)
        }
    }
}
