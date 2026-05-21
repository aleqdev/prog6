package org.example

import java.io.*
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.*

fun startServer(dbFilename: String) {
    val db = ArrayDequeueDatabase()

    // Создаём коллекцию
    db.createCollection<Ticket>(
        rules = listOf(
            DatabaseEntityRule { ticket, database, errors ->
                if (ticket.id <= 0) errors.fail("ID должен быть > 0")
                if (database.query<Ticket>()?.any { it.id == ticket.id } == true) {
                    errors.fail("ID должен быть уникальным")
                }
                if (ticket.name.isNullOrEmpty()) errors.fail("name не может быть пустым")
                if (ticket.coordinates == null) errors.fail("coordinates не может быть null")
                if (ticket.creationDate == null) errors.fail("creationDate не может быть null")
                if (ticket.price <= 0) errors.fail("price должен быть > 0")
                if (ticket.event == null) errors.fail("event не может быть null")

                ticket.coordinates?.let { coords ->
                    if (coords.x == null) errors.fail("coordinates.x не может быть null")
                    if (coords.y == null) errors.fail("coordinates.y не может быть null")
                    if (coords.y != null && coords.y <= -231.0) {
                        errors.fail("coordinates.y должен быть > -231")
                    }
                }

                ticket.event?.let { event ->
                    if (event.id <= 0) errors.fail("event.id должен быть > 0")
                    if (database.query<Ticket>()?.any { it.event?.id == event.id } == true) {
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
                val maxTicketId = tickets.maxOfOrNull { it.id } ?: 0
                val maxEventId = tickets.mapNotNull { it.event?.id }.maxOrNull() ?: 0

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
            deserializeTickets(reader).forEach { db.add(it) }
            reader.close()
            println("Загружено ${db.query<Ticket>()?.size ?: 0} билетов из $dbFilename")
        }
    } catch (e: Exception) {
        println("Не удалось загрузить данные: ${e.message}")
    }

    val commands = allCommands()
    val writer = OutputStreamWriter(System.`out`, "UTF-8")
    val history: MutableList<String> = mutableListOf()

    AppLogger.setLogger("Log4J2")
    // AppLogger.setLogger("Logback")
    // AppLogger.setLogger("Log4J2")

    serverMainLoop(
        db,
        commands,
        writer,
        dbFilename,
        history
    )
}

fun serverMainLoop(
    db: Database<*>,
    commands: List<Command<*>>,
    writer: OutputStreamWriter,
    dbFilename: String,
    history: MutableList<String> = mutableListOf()
) {
    val reader = InputStreamReader(System.`in`, "UTF-8")
    val readerScanner = Scanner(reader)

    val transport = ServerUdpTransport(8323, ServerClientsMap())
    val messenger = ServerCommandMessenger(transport)

    processCommandsPrompted(
        object : AsyncCommandScanner {
            override fun hasNextLine(): Boolean = readerScanner.hasNextLine()
            override fun nextLine(): String = readerScanner.nextLine()
            override fun spinUntilInputAvailable() {
                while (System.`in`.available() == 0) {
                    messenger.tryReceiveCommands().forEach { (senderId, cmd) ->
                        val (command, rawData) = cmd
                        val outputBuffer = ByteArrayOutputStream()
                        val outputWriter = OutputStreamWriter(outputBuffer)
                        executeReceivedCommand(
                            command,
                            rawData,
                            object : ServerCommandContext {
                                override fun registry(): CommandRegistry = object : CommandRegistry {
                                    override fun commands(): List<Command<*>> = commands
                                }
                                override fun output(): OutputStreamWriter = outputWriter
                                override fun shouldExit(): ShouldExitRef = ShouldExitRef(false)
                                override fun history(): MutableList<String> = history
                                override fun db(): Database<*> = db
                                override fun dbFilename(): String = dbFilename
                                override fun senderId(): ClientId = senderId
                                override fun serverClientsMap(): ServerClientsMap<*> = transport.clientsMap()
                                override fun executionHistory(): MutableList<String> = mutableListOf()
                            }
                        )
                        outputWriter.flush()
                        val result = outputBuffer.toString(StandardCharsets.UTF_8)
                        messenger.sendResponse(senderId, CommandResponse(result))
                    }
                    Thread.sleep(50L)
                }
            }
            override fun inner(): Scanner = readerScanner
        },
        writer,
        ShouldExitRef(false),
        commands,
        history,
        db,
        dbFilename,
        mutableListOf(),
        transport.clientsMap()
    )
}

fun executeReceivedCommand(
    command: Command<*>,
    data: Any?,
    context: ServerCommandContext
) {
    // UNCHECKED_CAST = мы не могли сериализовать некорректный <CD>, LocalCommand не сериализуется
    @Suppress("UNCHECKED_CAST")
    (command as ServerCommand<Any?>).processServer(context, data)
}
