package org.example

import java.io.OutputStreamWriter
import java.time.ZonedDateTime
import java.util.Scanner
import kotlin.reflect.KClass

/**
 * Обработчик команды на сервере
 */
interface ServerCommandHandler<T : Command> {
    fun name(): String
    fun description(): String
    fun commandClass(): KClass<T>
    fun adminCallable(): Boolean = true
    fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter): T?
    fun validate(command: T, context: ServerCommandContext): List<String> = emptyList()
    fun process(command: T, context: ServerCommandContext)
}

/**
 * Реестр обработчиков
 */
class ServerCommandRegistry {
    private val byClass: MutableMap<KClass<out Command>, ServerCommandHandler<out Command>> = mutableMapOf()
    private val byName: MutableMap<String, ServerCommandHandler<out Command>> = mutableMapOf()

    fun register(handler: ServerCommandHandler<out Command>) {
        byClass[handler.commandClass()] = handler
        byName[handler.name()] = handler
    }

    @Suppress("UNCHECKED_CAST")
    fun handlerFor(command: Command): ServerCommandHandler<Command>? =
        byClass[command::class] as? ServerCommandHandler<Command>

    @Suppress("UNCHECKED_CAST")
    fun handlerByName(name: String): ServerCommandHandler<Command>? =
        byName[name] as? ServerCommandHandler<Command>

    fun adminHandlers(): List<ServerCommandHandler<out Command>> =
        byClass.values.filter { it.adminCallable() }
}

/**
 * Серверная валидация полей Ticket
 */
private fun validateTicket(ticket: Ticket): List<String> {
    val errors = mutableListOf<String>()
    if (ticket.name.isNullOrEmpty()) errors.add("name не может быть пустым")
    if (ticket.price <= 0) errors.add("price должен быть > 0")
    val coords = ticket.coordinates
    if (coords == null) errors.add("coordinates не может быть null")
    else {
        if (coords.x == null) errors.add("coordinates.x не может быть null")
        val y = coords.y
        if (y == null) errors.add("coordinates.y не может быть null")
        else if (y <= -231.0) errors.add("coordinates.y должен быть > -231")
    }
    val event = ticket.event
    if (event == null) errors.add("event не может быть null")
    else {
        if (event.name.isNullOrEmpty()) errors.add("event.name не может быть пустым")
        if (event.date == null) errors.add("event.date не может быть null")
        if (event.eventType == null) errors.add("event.eventType не может быть null")
    }
    return errors
}

class InfoHandler : ServerCommandHandler<InfoCommand> {
    override fun name() = "info"
    override fun description() = "вывести информацию о коллекции (тип, дата инициализации, количество элементов)"
    override fun commandClass() = InfoCommand::class
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter) = InfoCommand()
    override fun process(command: InfoCommand, context: ServerCommandContext) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        context.output().write("Тип коллекции: ArrayDeque<Ticket>\n")
        context.output().write("Количество элементов: ${tickets.size}\n")
        context.output().write("Дата инициализации: ${ZonedDateTime.now()}\n")
        if (tickets.isNotEmpty()) {
            val min = tickets.stream().mapToLong { it.id }.min().asLong
            val max = tickets.stream().mapToLong { it.id }.max().asLong
            context.output().write("Диапазон ID: $min – $max\n")
        }
    }
}

class ShowHandler : ServerCommandHandler<ShowCommand> {
    override fun name() = "show"
    override fun description() = "вывести все элементы коллекции"
    override fun commandClass() = ShowCommand::class
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter) = ShowCommand()
    override fun process(command: ShowCommand, context: ServerCommandContext) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        if (tickets.isEmpty()) {
            context.output().write("Коллекция пуста\n")
            return
        }
        tickets.stream().sorted().forEach { context.output().write("$it\n") }
    }
}

class AddHandler : ServerCommandHandler<AddCommand> {
    override fun name() = "add"
    override fun description() = "добавить новый элемент в коллекцию"
    override fun commandClass() = AddCommand::class
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter): AddCommand? {
        val ticket = TerminalBuilder.build(Ticket::class, scanner) ?: return null
        return AddCommand(ticket)
    }

    override fun validate(command: AddCommand, context: ServerCommandContext) = validateTicket(command.ticket)
    override fun process(command: AddCommand, context: ServerCommandContext) {
        context.db().add(command.ticket, Ticket::class)
    }
}

class UpdateIdHandler : ServerCommandHandler<UpdateIdCommand> {
    override fun name() = "update_id"
    override fun description() = "обновить элемент коллекции по заданному id"
    override fun commandClass() = UpdateIdCommand::class
    override fun prepareFromConsole(
        args: List<String>,
        scanner: Scanner,
        output: OutputStreamWriter
    ): UpdateIdCommand? {
        if (args.isEmpty()) {
            output.write("Ошибка: не указан ID\n")
            return null
        }
        val id = args[0].toLongOrNull()
        if (id == null || id <= 0) {
            output.write("Ошибка: некорректный ID\n")
            return null
        }
        val ticket = TerminalBuilder.build(Ticket::class, scanner) ?: return null
        return UpdateIdCommand(id, ticket)
    }

    override fun validate(command: UpdateIdCommand, context: ServerCommandContext): List<String> {
        val errors = mutableListOf<String>()
        if (command.id <= 0) errors.add("id должен быть > 0")
        errors.addAll(validateTicket(command.ticket))
        return errors
    }

    override fun process(command: UpdateIdCommand, context: ServerCommandContext) {
        val targetId = command.id
        val exists = context.db().query(Ticket::class)?.stream()
            ?.anyMatch { it.id == targetId } ?: false
        if (!exists) {
            context.output().write("Нет элемента с таким id\n")
            return
        }
        val replacement = command.ticket.copy(id = targetId)
        context.db().update({ it.id == targetId }, {
            replacement.copy(creationDate = it.creationDate, event = replacement.event?.copy(id = it.event!!.id))
        }, Ticket::class)
    }
}

class RemoveByIdHandler : ServerCommandHandler<RemoveByIdCommand> {
    override fun name() = "remove_by_id"
    override fun description() = "удалить элемент из коллекции по его id"
    override fun commandClass() = RemoveByIdCommand::class
    override fun prepareFromConsole(
        args: List<String>,
        scanner: Scanner,
        output: OutputStreamWriter
    ): RemoveByIdCommand? {
        if (args.isEmpty()) {
            output.write("Ошибка: не указан ID\n")
            return null
        }
        val id = args[0].toLongOrNull()
        if (id == null || id <= 0) {
            output.write("Ошибка: некорректный ID\n")
            return null
        }
        return RemoveByIdCommand(id)
    }

    override fun validate(command: RemoveByIdCommand, context: ServerCommandContext): List<String> {
        return if (command.id <= 0) listOf("id должен быть > 0") else emptyList()
    }

    override fun process(command: RemoveByIdCommand, context: ServerCommandContext) {
        val targetId = command.id
        val exists = context.db().query(Ticket::class)?.stream()
            ?.anyMatch { it.id == targetId } ?: false
        if (!exists) {
            context.output().write("Нет элемента с таким id\n")
            return
        }
        context.db().remove({ it.id == targetId }, Ticket::class)
    }
}

class ClearHandler : ServerCommandHandler<ClearCommand> {
    override fun name() = "clear"
    override fun description() = "очистить коллекцию"
    override fun commandClass() = ClearCommand::class
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter) = ClearCommand()
    override fun process(command: ClearCommand, context: ServerCommandContext) {
        context.db().remove({ true }, Ticket::class)
        context.output().write("Коллекция очищена\n")
    }
}

class RemoveFirstHandler : ServerCommandHandler<RemoveFirstCommand> {
    override fun name() = "remove_first"
    override fun description() = "удалить первый элемент из коллекции"
    override fun commandClass() = RemoveFirstCommand::class
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter) =
        RemoveFirstCommand()

    override fun process(command: RemoveFirstCommand, context: ServerCommandContext) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        if (tickets.isEmpty()) {
            context.output().write("Коллекция пуста\n")
            return
        }
        val firstId = tickets.first().id
        context.db().remove({ it.id == firstId }, Ticket::class)
    }
}

class RemoveLowerHandler : ServerCommandHandler<RemoveLowerCommand> {
    override fun name() = "remove_lower"
    override fun description() = "удалить из коллекции все элементы, меньшие, чем заданный"
    override fun commandClass() = RemoveLowerCommand::class
    override fun prepareFromConsole(
        args: List<String>,
        scanner: Scanner,
        output: OutputStreamWriter
    ): RemoveLowerCommand? {
        val ticket = TerminalBuilder.build(Ticket::class, scanner) ?: return null
        return RemoveLowerCommand(ticket)
    }

    override fun validate(command: RemoveLowerCommand, context: ServerCommandContext) = validateTicket(command.ticket)
    override fun process(command: RemoveLowerCommand, context: ServerCommandContext) {
        val threshold = command.ticket
        context.db().remove({ it < threshold }, Ticket::class)
    }
}

class FilterContainsNameHandler : ServerCommandHandler<FilterContainsNameCommand> {
    override fun name() = "filter_contains_name"
    override fun description() = "вывести элементы, значение поля name которых содержит заданную подстроку"
    override fun commandClass() = FilterContainsNameCommand::class
    override fun prepareFromConsole(
        args: List<String>,
        scanner: Scanner,
        output: OutputStreamWriter
    ): FilterContainsNameCommand? {
        if (args.isEmpty()) {
            output.write("Ошибка: не указана подстрока\n")
            return null
        }
        return FilterContainsNameCommand(args[0])
    }

    override fun validate(command: FilterContainsNameCommand, context: ServerCommandContext): List<String> {
        return if (command.substring.isEmpty()) listOf("Подстрока не может быть пустой") else emptyList()
    }

    override fun process(command: FilterContainsNameCommand, context: ServerCommandContext) {
        val tickets = context.db().query(Ticket::class)
        if (tickets == null) {
            context.output().write("Коллекция пуста\n")
            return
        }
        tickets.stream()
            .filter { it.name?.contains(command.substring, ignoreCase = true) == true }
            .sorted()
            .forEach { context.output().write("$it\n") }
    }
}

class FilterLessThanTypeHandler : ServerCommandHandler<FilterLessThanTypeCommand> {
    override fun name() = "filter_less_than_type"
    override fun description() = "вывести элементы, значение поля type которых меньше заданного"
    override fun commandClass() = FilterLessThanTypeCommand::class
    override fun prepareFromConsole(
        args: List<String>,
        scanner: Scanner,
        output: OutputStreamWriter
    ): FilterLessThanTypeCommand? {
        if (args.isEmpty()) {
            output.write("Ошибка: не указан тип\n")
            return null
        }
        val target = parseEnum(TicketType::class, args[0]) as? TicketType
        if (target == null) {
            output.write("Некорректное значение типа\n")
            return null
        }
        return FilterLessThanTypeCommand(target)
    }

    override fun process(command: FilterLessThanTypeCommand, context: ServerCommandContext) {
        val tickets = context.db().query(Ticket::class)
        if (tickets == null) {
            context.output().write("Коллекция пуста\n")
            return
        }
        tickets.stream()
            .filter { ticket ->
                val type = ticket.type
                type != null && type < command.type
            }
            .sorted()
            .forEach { context.output().write("$it\n") }
    }
}

class PrintDescendingHandler : ServerCommandHandler<PrintDescendingCommand> {
    override fun name() = "print_descending"
    override fun description() = "вывести элементы коллекции в порядке убывания"
    override fun commandClass() = PrintDescendingCommand::class
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter) =
        PrintDescendingCommand()

    override fun process(command: PrintDescendingCommand, context: ServerCommandContext) {
        val tickets = context.db().query(Ticket::class) ?: emptyList()
        if (tickets.isEmpty()) {
            context.output().write("Коллекция пуста\n")
            return
        }
        tickets.stream()
            .sorted(Comparator.reverseOrder())
            .forEach { context.output().write("$it\n") }
    }
}

class DisconnectHandler : ServerCommandHandler<DisconnectCommand> {
    override fun name() = "(disconnect)"
    override fun description() = "служебный сигнал клиентского транспорта об отключении"
    override fun commandClass() = DisconnectCommand::class
    override fun adminCallable() = false
    override fun prepareFromConsole(args: List<String>, scanner: Scanner, output: OutputStreamWriter): DisconnectCommand? = null
    override fun process(command: DisconnectCommand, context: ServerCommandContext) {
        val id = context.senderId() ?: return
        context.clientsMap().removeClient(id)
        AppLogger.log("[$id] отключен")
    }
}

private fun parseEnum(type: KClass<*>, string: String): Any? {
    if (!type.java.isEnum) return null
    return type.java.enumConstants.find { it.toString().equals(string, ignoreCase = true) }
}

fun buildServerCommandRegistry(): ServerCommandRegistry {
    val registry = ServerCommandRegistry()
    registry.register(InfoHandler())
    registry.register(ShowHandler())
    registry.register(AddHandler())
    registry.register(UpdateIdHandler())
    registry.register(RemoveByIdHandler())
    registry.register(ClearHandler())
    registry.register(RemoveFirstHandler())
    registry.register(RemoveLowerHandler())
    registry.register(FilterContainsNameHandler())
    registry.register(FilterLessThanTypeHandler())
    registry.register(PrintDescendingHandler())
    registry.register(DisconnectHandler())
    return registry
}
