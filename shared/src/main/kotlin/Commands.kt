package org.example

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.File
import java.io.OutputStreamWriter
import java.util.Scanner
import kotlin.reflect.KClass

/**
 * Интерфейс команды (CD - CommandData)
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS, // Используем полное имя класса
    property = "@command"    // Имя поля в JSON
)
interface Command<CD> {
    fun name(): String
    fun description(): String
    fun prepare(context: CommandPrepareContext): CD?
}

interface LocalCommand<CD> : Command<CD> {
    fun processLocal(context: LocalCommandContext, data: CD)
}

interface ServerCommand<CD> : Command<CD> {
    fun processServer(context: ServerCommandContext, data: CD)
}

interface AsyncCommandScanner {
    fun hasNextLine(): Boolean
    fun nextLine(): String
    fun spinUntilInputAvailable(): Unit
    fun inner(): Scanner
}

interface CommandCommonContext {
    fun registry(): CommandRegistry
    fun output(): OutputStreamWriter
}

interface CommandPrepareContext : CommandCommonContext {
    fun scanner(): AsyncCommandScanner
    fun args(): List<String>
    fun executionHistory(): MutableList<String>
}

interface LocalCommandContext : CommandCommonContext {
    fun shouldExit(): ShouldExitRef
    fun history(): MutableList<String>
}

interface ServerCommandContext : LocalCommandContext {
    fun db(): Database<*>
    fun dbFilename(): String
    fun senderId(): ClientId?
    fun serverClientsMap(): ServerClientsMap<*>
    fun executionHistory(): MutableList<String>
}

/**
 * Все доступные команды
 */
interface CommandRegistry {
    fun commands(): List<Command<*>>
}

/**
 * Команда помощи: вывод списка доступных команд
 */
class HelpCommand @JsonCreator constructor() : LocalCommand<Unit> {
    override fun name(): String = "help"
    override fun description(): String = "вывести справку по доступным командам"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processLocal(context: LocalCommandContext, data: Unit) {
        context.output().write("Доступные команды:\n")
        context.registry().commands().forEach {
            context.output().write("  ${it.name()} - ${it.description()}\n")
        }
    }
}

/**
 * Команда info: информация о коллекции
 */
class InfoCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "info"
    override fun description(): String = "вывести информацию о коллекции (тип, дата инициализации, количество элементов)"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        context.output().write("Тип коллекции: ArrayDeque<Ticket>\n")
        context.output().write("Количество элементов: ${tickets.size}\n")
        context.output().write("Дата инициализации: ${java.time.ZonedDateTime.now()}\n")
        if (tickets.isNotEmpty()) {
            context.output().write("Диапазон ID: ${tickets.minOf { it.id }} – ${tickets.maxOf { it.id }}\n")
        }
    }
}

/**
 * Команда show: вывод всех элементов коллекции
 */
class ShowCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "show"
    override fun description(): String = "вывести все элементы коллекции в строковом представлении"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        if (tickets.isEmpty()) {
            context.output().write("Коллекция пуста\n")
            return
        }
        tickets.forEach { context.output().write("${it}\n") }
    }
}

/**
 * Команда add: добавление нового элемента
 */
class AddCommand @JsonCreator constructor() : ServerCommand<Ticket> {
    override fun name(): String = "add"
    override fun description(): String = "добавить новый элемент в коллекцию"
    override fun prepare(context: CommandPrepareContext): Ticket? {
        return TerminalBuilder.build(Ticket::class, context.scanner().inner())
    }
    override fun processServer(context: ServerCommandContext, data: Ticket) {
        context.db().add(data, Ticket::class)
    }
}

/**
 * Команда update_id: обновление элемента по ID
 */
class UpdateIdCommand @JsonCreator constructor() : ServerCommand<Ticket> {
    override fun name(): String = "update_id"
    override fun description(): String = "обновить элемент коллекции по заданному id"
    override fun prepare(context: CommandPrepareContext): Ticket? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан ID\n")
            return null
        }
        val id = context.args()[0].toLongOrNull()
        if (id == null) {
            context.output().write("Ошибка: некорректный ID\n")
            return null
        }
        return TerminalBuilder.build(Ticket::class, context.scanner().inner())!!.copy(id = id)
    }
    override fun processServer(context: ServerCommandContext, data: Ticket) {
        if (context.db().query(Ticket::class)?.none { it.id == data.id } == true) {
            context.output().write("Нет элемента с таким id\n")
        } else {
            context.db().update({ it.id == data.id }, { data }, Ticket::class)
        }
    }
}

/**
 * Команда remove_by_id: удаление элемента по ID
 */
class RemoveByIdCommand @JsonCreator constructor() : ServerCommand<Long> {
    override fun name(): String = "remove_by_id"
    override fun description(): String = "удалить элемент из коллекции по его id"
    override fun prepare(context: CommandPrepareContext): Long? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан ID\n")
            return null
        }
        return context.args()[0].toLongOrNull()
    }
    override fun processServer(context: ServerCommandContext, data: Long) {
        if (context.db().query(Ticket::class)?.none { it.id == data } == true) {
            context.output().write("Нет элемента с таким id\n")
        } else {
            context.db().remove({ it.id == data }, Ticket::class)
        }
    }
}

/**
 * Команда clear: очистка коллекции
 */
class ClearCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "clear"
    override fun description(): String = "очистить коллекцию"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        context.db().remove({ true }, Ticket::class)
        context.output().write("Коллекция очищена\n")
    }
}

/**
 * Команда save: сохранение коллекции в файл
 */
class SaveCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "save"
    override fun description(): String = "сохранить коллекцию в файл"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        File(context.dbFilename()).writer().use { writer ->
            serializeTickets(tickets, writer)
            context.output().write("Данные сохранены\n")
        }
    }
}

/**
 * Команда execute_script: выполнение скрипта из файла
 */
class ExecuteScriptCommand @JsonCreator constructor() : ServerCommand<String> {
    override fun name(): String = "execute_script"
    override fun description(): String = "считать и исполнить скрипт из указанного файла"
    override fun prepare(context: CommandPrepareContext): String? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан путь к файлу скрипта\n")
            return null
        }
        val fileName = context.args()[0]
        if (context.executionHistory().contains(fileName)) {
            context.output().write("Ошибка: рекурсия\n")
            return ""
        }
        context.executionHistory().add(fileName)
        File(fileName).reader().use { reader ->
            return reader.readText()
        }
    }
    override fun processServer(context: ServerCommandContext, data: String) {
        val contentsScanner = Scanner(data)
        processCommands(
            object : AsyncCommandScanner {
                override fun hasNextLine(): Boolean = contentsScanner.hasNextLine()
                override fun nextLine(): String = contentsScanner.nextLine()
                override fun spinUntilInputAvailable() {
                    // Файл всегда полностью доступен, ниче не делаем
                }
                override fun inner(): Scanner = contentsScanner
            },
            context.output(),
            context.shouldExit(),
            context.registry().commands(),
            context.history(),
            context.db(),
            context.dbFilename(),
            context.executionHistory(),
            context.serverClientsMap(),
            {}
        )
        if (context.executionHistory().isNotEmpty()) {
            context.executionHistory().removeLast()
        }
    }
}

/**
 * Команда exit: завершение программы
 */
class ExitCommand @JsonCreator constructor() : LocalCommand<Unit> {
    override fun name(): String = "exit"
    override fun description(): String = "завершить программу (без сохранения в файл)"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processLocal(context: LocalCommandContext, data: Unit) {
        context.output().write("Завершение работы...\n")
        context.shouldExit().value = true
    }
}

/**
 * Команда remove_first: удаление первого элемента
 */
class RemoveFirstCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "remove_first"
    override fun description(): String = "удалить первый элемент из коллекции"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        if (tickets.isEmpty()) {
            context.output().write("Коллекция пуста\n")
            return
        }
        val firstId = tickets.first().id
        context.db().remove({ it.id == firstId }, Ticket::class)
    }
}

/**
 * Команда remove_lower: удаление элементов, меньших заданного
 */
class RemoveLowerCommand @JsonCreator constructor() : ServerCommand<Ticket> {
    override fun name(): String = "remove_lower"
    override fun description(): String = "удалить из коллекции все элементы, меньшие, чем заданный"
    override fun prepare(context: CommandPrepareContext): Ticket? {
        return TerminalBuilder.build(Ticket::class, context.scanner().inner())
    }
    override fun processServer(context: ServerCommandContext, data: Ticket) {
        context.db().remove({ it < data }, Ticket::class)
    }
}

/**
 * Команда history: вывод последних 8 команд
 */
class HistoryCommand @JsonCreator constructor() : LocalCommand<Unit> {
    override fun name(): String = "history"
    override fun description(): String = "вывести последние 8 команд (без их аргументов)"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processLocal(context: LocalCommandContext, data: Unit) {
        val history = context.history().takeLast(8)
        if (history.isEmpty()) {
            context.output().write("История пуста\n")
            return
        }
        history.forEachIndexed { i, cmd -> context.output().write("${i + 1}. $cmd\n") }
    }
}

/**
 * Команда filter_contains_name: фильтрация по подстроке в name
 */
class FilterContainsNameCommand @JsonCreator constructor() : ServerCommand<String> {
    override fun name(): String = "filter_contains_name"
    override fun description(): String = "вывести элементы, значение поля name которых содержит заданную подстроку"
    override fun prepare(context: CommandPrepareContext): String? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указана подстрока\n")
            return null
        }
        return context.args()[0]
    }
    override fun processServer(context: ServerCommandContext, data: String) {
        context.db().query(Ticket::class)?.filter {
            it.name?.contains(data, ignoreCase = true) == true
        }?.forEach { context.output().write("${it}\n") } ?: context.output().write("Коллекция пуста\n")
    }
}

/**
 * Команда filter_less_than_type: фильтрация по типу билета
 */
class FilterLessThanTypeCommand @JsonCreator constructor() : ServerCommand<TicketType> {
    override fun name(): String = "filter_less_than_type"
    override fun description(): String = "вывести элементы, значение поля type которых меньше заданного"
    override fun prepare(context: CommandPrepareContext): TicketType? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан тип\n")
            return null
        }
        val targetType = parseEnum(TicketType::class, context.args()[0]) as? TicketType
        if (targetType == null) {
            context.output().write("Некорректное значение типа\n")
            return null
        }
        return targetType
    }
    override fun processServer(context: ServerCommandContext, data: TicketType) {
        context.db().query(Ticket::class)?.filter {
            it.type != null && it.type < data
        }?.forEach { context.output().write("${it}\n") } ?: context.output().write("Коллекция пуста\n")
    }
}

/**
 * Команда print_descending: вывод элементов в порядке убывания
 */
class PrintDescendingCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "print_descending"
    override fun description(): String = "вывести элементы коллекции в порядке убывания"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        if (tickets.isEmpty()) {
            context.output().write("Коллекция пуста\n")
            return
        }
        tickets.sortedDescending().forEach { context.output().write("${it}\n") }
    }
}

/**
 * Внутренняя команда, которая не может быть вызвана вручную
 * Ей клиент уведомляет сервак что отключается -> серверу больше не нужно хранить его id.
 * в allCommands() не присутствует.
 */
class DisconnectClientCommand @JsonCreator constructor() : ServerCommand<Unit> {
    override fun name(): String = "(internal)"
    override fun description(): String = "отключает клиента, нельзя вызвать вручную"
    override fun prepare(context: CommandPrepareContext) { return }
    override fun processServer(context: ServerCommandContext, data: Unit) {
        val id = context.senderId()!!
        context.serverClientsMap().removeClient(id)
        AppLogger.log("[$id] отключен")
    }
}

fun parseEnum(type: KClass<*>, string: String): Any? {
    if (!type.java.isEnum) return null
    val constants = type.java.enumConstants
    return constants.find { it.toString().equals(string, ignoreCase = true) }
}

fun allClientCommands(): List<Command<*>> = listOf(
    HelpCommand(), InfoCommand(), ShowCommand(), AddCommand(), UpdateIdCommand(),
    RemoveByIdCommand(), ClearCommand(), ExecuteScriptCommand(),
    ExitCommand(), RemoveFirstCommand(), RemoveLowerCommand(), HistoryCommand(),
    FilterContainsNameCommand(), FilterLessThanTypeCommand(), PrintDescendingCommand()
)

fun allCommands(): List<Command<*>> = allClientCommands() + listOf(SaveCommand())

data class ShouldExitRef(var value: Boolean)

fun processCommands(
    asyncScanner: AsyncCommandScanner,
    output: OutputStreamWriter,
    shouldExit: ShouldExitRef,
    commands: List<Command<*>>,
    history: MutableList<String>,
    db: Database<*>,
    dbFilename: String,
    executionHistory: MutableList<String>,
    serverClientsMap: ServerClientsMap<*>,
    beforeReadLineCallback: () -> Unit,
) {
    while (!shouldExit.value) {
        beforeReadLineCallback()
        output.flush()
        asyncScanner.spinUntilInputAvailable()
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
            override fun executionHistory(): MutableList<String> = executionHistory
        }

        val ctx = object : ServerCommandContext {
            override fun registry(): CommandRegistry = object : CommandRegistry {
                override fun commands(): List<Command<*>> = commands
            }

            override fun output(): OutputStreamWriter = output
            override fun shouldExit(): ShouldExitRef = shouldExit
            override fun history(): MutableList<String> = history
            override fun db(): Database<*> = db
            override fun dbFilename(): String = dbFilename
            override fun senderId(): ClientId? = null
            override fun executionHistory(): MutableList<String> = executionHistory
            override fun serverClientsMap(): ServerClientsMap<*> = serverClientsMap
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
                        typedCommand.processServer(ctx, data)
                    }
                }
                else -> throw IllegalArgumentException(command::class.simpleName)
            }
            output.flush()
            history.add(name)
            if (history.size > 8) history.removeAt(0)
        } else {
            output.write("Неизвестная команда: $name. Введите 'help'\n")
            output.flush()
        }
    }
}

fun processCommandsPrompted(
    asyncScanner: AsyncCommandScanner,
    output: OutputStreamWriter,
    shouldExit: ShouldExitRef,
    commands: List<Command<*>>,
    history: MutableList<String>,
    db: Database<*>,
    dbFilename: String,
    executionHistory: MutableList<String>,
    serverClientsMap: ServerClientsMap<*>
) {
    processCommands(
        asyncScanner,
        output,
        shouldExit,
        commands,
        history,
        db,
        dbFilename,
        executionHistory,
        serverClientsMap
    ) { print("> ") }
}
