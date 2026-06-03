package org.example

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.Scanner

private const val UDP_PORT = 8323

fun startServer(dbFilename: String) {
    AppLogger.setLogger("Log4J2")

    val db = ArrayDequeueDatabase()

    db.createCollection<Ticket>(
        rules = listOf(
            DatabaseEntityRule { ticket, database, errors ->
                if (ticket.id <= 0) errors.fail("ID должен быть > 0")
                if (database.query<Ticket>()?.stream()?.anyMatch { it.id == ticket.id } == true) {
                    errors.fail("ID должен быть уникальным")
                }
                if (ticket.name.isNullOrEmpty()) errors.fail("name не может быть пустым")
                if (ticket.coordinates == null) errors.fail("coordinates не может быть null")
                if (ticket.creationDate == null) errors.fail("creationDate не может быть null")
                if (ticket.price <= 0) errors.fail("price должен быть > 0")
                if (ticket.event == null) errors.fail("event не может быть null")

                ticket.coordinates?.let { coords ->
                    if (coords.x == null) errors.fail("coordinates.x не может быть null")
                    val y = coords.y
                    if (y == null) errors.fail("coordinates.y не может быть null")
                    if (y != null && y <= -231.0) {
                        errors.fail("coordinates.y должен быть > -231")
                    }
                }

                ticket.event?.let { event ->
                    if (event.id <= 0) errors.fail("event.id должен быть > 0")
                    if (database.query<Ticket>()?.stream()?.anyMatch { it.event?.id == event.id } == true) {
                        errors.fail("event.id должен быть уникальным")
                    }
                    if (event.name.isNullOrEmpty()) errors.fail("event.name не может быть пустым")
                    if (event.date == null) errors.fail("event.date не может быть null")
                    if (event.eventType == null) errors.fail("event.eventType не может быть null")
                }
            }
        ),
        prepopulate = listOf(
            DatabaseEntityPrepopulate { ticket, database ->
                val tickets = database.query<Ticket>() ?: emptyList()
                val maxTicketId = tickets.stream().mapToLong { it.id }.max().orElse(0L)
                val maxEventId = tickets.stream()
                    .mapToLong { it.event?.id ?: 0L }
                    .max()
                    .orElse(0L)

                ticket.copy(
                    id = maxTicketId + 1,
                    creationDate = ZonedDateTime.now(),
                    event = ticket.event?.copy(id = maxEventId + 1)
                )
            }
        )
    )

    try {
        val file = File(dbFilename)
        if (file.exists() && file.canRead()) {
            val reader = InputStreamReader(FileInputStream(file), "UTF-8")
            deserializeTickets(reader).forEach { db.addNonPopulated(it) }
            reader.close()
            AppLogger.log("Загружено ${db.query<Ticket>()?.size ?: 0} билетов из $dbFilename")
        }
    } catch (e: Exception) {
        AppLogger.log("Не удалось загрузить данные: ${e.message}")
    }

    val registry = buildServerCommandRegistry()
    val writer = OutputStreamWriter(System.`out`, "UTF-8")

    Runtime.getRuntime().addShutdownHook(Thread {
        AppLogger.log("Завершение работы, автосохранение в $dbFilename")
        runCatching { saveCollection(db, dbFilename) }
            .onFailure { AppLogger.log("Ошибка автосохранения: ${it.message}") }
    })

    serverMainLoop(db, registry, writer, dbFilename)
}

fun serverMainLoop(
    db: ArrayDequeueDatabase,
    registry: ServerCommandRegistry,
    writer: OutputStreamWriter,
    dbFilename: String,
) {
    val transport = ServerUdpTransport(UDP_PORT, ServerClientsMap())
    val messenger = ServerCommandMessenger(transport)

    val stdinReader = InputStreamReader(System.`in`, "UTF-8")
    val stdinScanner = Scanner(stdinReader)

    writer.write("> ")

    var running = true
    while (running) {
        messenger.tryReceiveCommands().forEach { incoming ->
            when (incoming) {
                is IncomingMessage.Malformed -> {
                    AppLogger.log("[${incoming.senderId}] невалидное сообщение: ${incoming.reason}")
                    messenger.sendResponse(
                        incoming.senderId,
                        CommandResponse("Ошибка: невалидное сообщение или неизвестная команда\n")
                    )
                }
                is IncomingMessage.Parsed -> handleNetworkCommand(
                    incoming.senderId,
                    incoming.command,
                    registry,
                    db,
                    dbFilename,
                    transport,
                    messenger
                )
            }
        }

        if (System.`in`.available() > 0 && stdinScanner.hasNextLine()) {
            val line = stdinScanner.nextLine().trim()
            if (line.isNotEmpty()) {
                handleServerLine(line, registry, db, dbFilename, writer, stdinScanner) { running = false }
                writer.write("> ")
                writer.flush()
            }
        }

        Thread.sleep(50L)
    }

    transport.close()
}

private fun handleServerLine(
    line: String,
    registry: ServerCommandRegistry,
    db: ArrayDequeueDatabase,
    dbFilename: String,
    writer: OutputStreamWriter,
    scanner: Scanner,
    requestShutdown: () -> Unit,
) {
    val parts = line.split(Regex("\\s+"))
    val name = parts.first()
    val args = parts.drop(1)

    when (name) {
        "save" -> {
            runCatching { saveCollection(db, dbFilename) }
                .onSuccess { writer.write("Данные сохранены\n") }
                .onFailure { writer.write("Ошибка сохранения: ${it.message}\n") }
            writer.flush()
        }
        "exit" -> {
            writer.write("Завершение работы...\n")
            writer.flush()
            requestShutdown()
        }
        "help" -> {
            writer.write("Команды сервера:\n")
            writer.write("  save - сохранить коллекцию в файл\n")
            writer.write("  exit - завершить работу сервера\n")
            writer.write("  help - вывести этот список\n")
            registry.adminHandlers().forEach {
                writer.write("  ${it.name()} - ${it.description()}\n")
            }
            writer.flush()
        }
        else -> {
            val handler = registry.handlerByName(name)
            if (handler == null || !handler.adminCallable()) {
                writer.write("Неизвестная серверная команда: '$name'. Введите 'help'.\n")
                writer.flush()
                return
            }
            val command = handler.prepareFromConsole(args, scanner, writer) ?: run {
                writer.flush(); return
            }
            val ctx = serverContext(db, dbFilename, writer)
            val errors = handler.validate(command, ctx)
            if (errors.isNotEmpty()) {
                writer.write("Ошибки валидации:\n")
                errors.forEach { writer.write("  $it\n") }
            } else {
                handler.process(command, ctx)
            }
            writer.flush()
        }
    }
}

private fun serverContext(
    db: ArrayDequeueDatabase,
    dbFilename: String,
    writer: OutputStreamWriter,
): ServerCommandContext = object : ServerCommandContext {
    override fun db() = db
    override fun output() = writer
    override fun clientsMap(): ServerClientsMap<*> = ServerClientsMap<Any>()
    override fun senderId(): ClientId? = null
    override fun dbFilename() = dbFilename
}

/**
 * исполнение одной сетевой команды.
 */
fun handleNetworkCommand(
    senderId: ClientId,
    command: Command,
    registry: ServerCommandRegistry,
    db: ArrayDequeueDatabase,
    dbFilename: String,
    transport: ServerTransport,
    messenger: ServerCommandMessenger,
) {
    val outputBuffer = ByteArrayOutputStream()
    val outputWriter = OutputStreamWriter(outputBuffer, StandardCharsets.UTF_8)

    val ctx = object : ServerCommandContext {
        override fun db() = db
        override fun output() = outputWriter
        override fun clientsMap() = transport.clientsMap()
        override fun senderId() = senderId
        override fun dbFilename() = dbFilename
    }

    val handler = registry.handlerFor(command)
    if (handler == null) {
        outputWriter.write("Команда не разрешена: ${command.name()}\n")
    } else {
        val errors = handler.validate(command, ctx)
        if (errors.isNotEmpty()) {
            outputWriter.write("Ошибки валидации:\n")
            errors.forEach { outputWriter.write("  $it\n") }
        } else {
            handler.process(command, ctx)
        }
    }

    outputWriter.flush()
    val result = outputBuffer.toString(StandardCharsets.UTF_8)

    // DisconnectCommand — служебное сообщение, отвечать клиенту не нужно
    if (command !is DisconnectCommand) {
        messenger.sendResponse(senderId, CommandResponse(result))
    }
}

/**
 * Сохранение коллекции в файл
 */
fun saveCollection(db: ArrayDequeueDatabase, dbFilename: String) {
    val tickets = db.query<Ticket>() ?: emptyList()
    File(dbFilename).writer().use { writer ->
        serializeTickets(tickets, writer)
    }
}
