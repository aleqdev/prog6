package org.example

import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.SocketTimeoutException
import java.util.Scanner

fun startClient() {
    val writer = OutputStreamWriter(System.`out`, "UTF-8")
    val history: MutableList<String> = mutableListOf()
    val shouldExit = ShouldExitRef(false)

    val reader = InputStreamReader(System.`in`, "UTF-8")
    val stdinScanner = Scanner(reader)

    while (!shouldExit.value) {
        try {
            ClientUdpTransport("localhost", 8323).use { transport ->
                ClientCommandMessenger(transport).use { messenger ->
                    runClientLoop(stdinScanner, writer, history, shouldExit, messenger)
                }
            }
        } catch (e: Exception) {
            writer.write("Ошибка соединения: ${e.message}\n")
            writer.flush()
            Thread.sleep(500L)
        }
    }
}

private fun runClientLoop(
    stdinScanner: Scanner,
    output: OutputStreamWriter,
    history: MutableList<String>,
    shouldExit: ShouldExitRef,
    messenger: ClientCommandMessenger,
) {
    lateinit var locals: List<LocalClientCommand>
    val networks: List<NetworkClientCommand> = buildNetworkCommands()

    val runLine: (String, ClientCommandContext) -> Unit = { line, ctx ->
        dispatch(line, ctx, messenger, output, history)
    }
    locals = buildLocalCommands(runLine)

    while (!shouldExit.value) {
        output.flush()
        print("> ")
        if (!stdinScanner.hasNextLine()) break

        val line = stdinScanner.nextLine().trim()
        if (line.isEmpty()) continue

        val ctx = makeContext(stdinScanner, output, history, shouldExit, locals, networks)
        dispatch(line, ctx, messenger, output, history)
    }
}

private fun dispatch(
    line: String,
    context: ClientCommandContext,
    messenger: ClientCommandMessenger,
    output: OutputStreamWriter,
    history: MutableList<String>,
) {
    val parts = line.split(Regex("\\s+"))
    val name = parts.first()
    val args = parts.drop(1)
    val withArgs = withArgs(context, args)

    val local = context.localCommands().firstOrNull { it.name() == name }
    if (local != null) {
        local.execute(withArgs)
        history.add(name)
        if (history.size > 8) history.removeAt(0)
        return
    }

    val network = context.networkCommands().firstOrNull { it.name() == name }
    if (network != null) {
        val command = network.prepare(withArgs) ?: return
        sendCommand(command, messenger, output)
        history.add(name)
        if (history.size > 8) history.removeAt(0)
        return
    }

    output.write("Неизвестная команда: $name. Введите 'help'\n")
}

private fun sendCommand(
    command: Command,
    messenger: ClientCommandMessenger,
    output: OutputStreamWriter,
) {
    messenger.sendCommand(command)
    var got = false
    repeat(60) {
        if (got) return@repeat
        messenger.tryReceiveResponse()?.let { resp ->
            output.write(resp.output)
            got = true
            return@repeat
        }
        Thread.sleep(50L)
    }
    if (!got) throw SocketTimeoutException("таймаут сервера")
}

private fun makeContext(
    scanner: Scanner,
    output: OutputStreamWriter,
    history: MutableList<String>,
    shouldExit: ShouldExitRef,
    locals: List<LocalClientCommand>,
    networks: List<NetworkClientCommand>,
): ClientCommandContext {
    val execHistory: MutableList<String> = mutableListOf()
    return object : ClientCommandContext {
        override fun output() = output
        override fun scanner() = scanner
        override fun args(): List<String> = emptyList()
        override fun history() = history
        override fun shouldExit() = shouldExit
        override fun executionHistory() = execHistory
        override fun localCommands() = locals
        override fun networkCommands() = networks
    }
}

private fun withArgs(base: ClientCommandContext, newArgs: List<String>): ClientCommandContext =
    object : ClientCommandContext by base {
        override fun args(): List<String> = newArgs
    }
