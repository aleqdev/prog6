package org.example

import java.io.File
import java.util.Scanner

class HelpClientCommand : LocalClientCommand {
    override fun name() = "help"
    override fun description() = "вывести справку по доступным командам"
    override fun execute(context: ClientCommandContext) {
        context.output().write("Доступные команды:\n")
        context.localCommands().forEach {
            context.output().write("  ${it.name()} - ${it.description()}\n")
        }
        context.networkCommands().forEach {
            context.output().write("  ${it.name()} - ${it.description()}\n")
        }
    }
}

class HistoryClientCommand : LocalClientCommand {
    override fun name() = "history"
    override fun description() = "вывести последние 8 команд (без их аргументов)"
    override fun execute(context: ClientCommandContext) {
        val history = context.history().takeLast(8)
        if (history.isEmpty()) {
            context.output().write("История пуста\n")
            return
        }
        history.forEachIndexed { i, cmd -> context.output().write("${i + 1}. $cmd\n") }
    }
}

class ExitClientCommand : LocalClientCommand {
    override fun name() = "exit"
    override fun description() = "завершить клиент (без сохранения на сервере)"
    override fun execute(context: ClientCommandContext) {
        context.output().write("Завершение работы...\n")
        context.shouldExit().value = true
    }
}

class ExecuteScriptClientCommand(
    private val runLine: (String, ClientCommandContext) -> Unit
) : LocalClientCommand {
    override fun name() = "execute_script"
    override fun description() = "считать и исполнить скрипт из указанного файла"

    override fun execute(context: ClientCommandContext) {
        val args = context.args()
        if (args.isEmpty()) {
            context.output().write("Ошибка: не указан путь к файлу скрипта\n")
            return
        }
        val fileName = args[0]
        if (context.executionHistory().contains(fileName)) {
            context.output().write("Ошибка: рекурсия в скрипте '$fileName'\n")
            return
        }
        context.executionHistory().add(fileName)
        try {
            val text = File(fileName).reader().use { it.readText() }
            val scriptScanner = Scanner(text)
            while (scriptScanner.hasNextLine()) {
                val line = scriptScanner.nextLine().trim()
                if (line.isEmpty()) continue
                val scriptCtx = withScanner(context, scriptScanner, line)
                runLine(line, scriptCtx)
            }
        } catch (e: Exception) {
            context.output().write("Ошибка скрипта: ${e.message}\n")
        } finally {
            if (context.executionHistory().isNotEmpty()) context.executionHistory().removeLast()
        }
    }

    private fun withScanner(
        base: ClientCommandContext,
        newScanner: Scanner,
        rawLine: String,
    ): ClientCommandContext = object : ClientCommandContext by base {
        override fun scanner(): Scanner = newScanner
    }
}

class InfoClientCommand : NetworkClientCommand {
    override fun name() = "info"
    override fun description() = "вывести информацию о коллекции (тип, дата инициализации, количество элементов)"
    override fun prepare(context: ClientCommandContext) = InfoCommand()
}

class ShowClientCommand : NetworkClientCommand {
    override fun name() = "show"
    override fun description() = "вывести все элементы коллекции"
    override fun prepare(context: ClientCommandContext) = ShowCommand()
}

class AddClientCommand : NetworkClientCommand {
    override fun name() = "add"
    override fun description() = "добавить новый элемент в коллекцию"
    override fun prepare(context: ClientCommandContext): Command? {
        val ticket = TerminalBuilder.build(Ticket::class, context.scanner()) ?: return null
        return AddCommand(ticket)
    }
}

class UpdateIdClientCommand : NetworkClientCommand {
    override fun name() = "update_id"
    override fun description() = "обновить элемент коллекции по заданному id"
    override fun prepare(context: ClientCommandContext): Command? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан ID\n")
            return null
        }
        val id = context.args()[0].toLongOrNull()
        if (id == null || id <= 0) {
            context.output().write("Ошибка: некорректный ID\n")
            return null
        }
        val ticket = TerminalBuilder.build(Ticket::class, context.scanner()) ?: return null
        return UpdateIdCommand(id, ticket)
    }
}

class RemoveByIdClientCommand : NetworkClientCommand {
    override fun name() = "remove_by_id"
    override fun description() = "удалить элемент из коллекции по его id"
    override fun prepare(context: ClientCommandContext): Command? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан ID\n")
            return null
        }
        val id = context.args()[0].toLongOrNull()
        if (id == null || id <= 0) {
            context.output().write("Ошибка: некорректный ID\n")
            return null
        }
        return RemoveByIdCommand(id)
    }
}

class ClearClientCommand : NetworkClientCommand {
    override fun name() = "clear"
    override fun description() = "очистить коллекцию"
    override fun prepare(context: ClientCommandContext) = ClearCommand()
}

class RemoveFirstClientCommand : NetworkClientCommand {
    override fun name() = "remove_first"
    override fun description() = "удалить первый элемент из коллекции"
    override fun prepare(context: ClientCommandContext) = RemoveFirstCommand()
}

class RemoveLowerClientCommand : NetworkClientCommand {
    override fun name() = "remove_lower"
    override fun description() = "удалить из коллекции все элементы, меньшие, чем заданный"
    override fun prepare(context: ClientCommandContext): Command? {
        val ticket = TerminalBuilder.build(Ticket::class, context.scanner()) ?: return null
        return RemoveLowerCommand(ticket)
    }
}

class FilterContainsNameClientCommand : NetworkClientCommand {
    override fun name() = "filter_contains_name"
    override fun description() = "вывести элементы, значение поля name которых содержит заданную подстроку"
    override fun prepare(context: ClientCommandContext): Command? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указана подстрока\n")
            return null
        }
        return FilterContainsNameCommand(context.args()[0])
    }
}

class FilterLessThanTypeClientCommand : NetworkClientCommand {
    override fun name() = "filter_less_than_type"
    override fun description() = "вывести элементы, значение поля type которых меньше заданного"
    override fun prepare(context: ClientCommandContext): Command? {
        if (context.args().isEmpty()) {
            context.output().write("Ошибка: не указан тип\n")
            return null
        }
        val target = parseEnum(TicketType::class, context.args()[0]) as? TicketType
        if (target == null) {
            context.output().write("Некорректное значение типа\n")
            return null
        }
        return FilterLessThanTypeCommand(target)
    }
}

class PrintDescendingClientCommand : NetworkClientCommand {
    override fun name() = "print_descending"
    override fun description() = "вывести элементы коллекции в порядке убывания"
    override fun prepare(context: ClientCommandContext) = PrintDescendingCommand()
}

private fun parseEnum(type: kotlin.reflect.KClass<*>, string: String): Any? {
    if (!type.java.isEnum) return null
    return type.java.enumConstants.find { it.toString().equals(string, ignoreCase = true) }
}

fun buildLocalCommands(runLine: (String, ClientCommandContext) -> Unit): List<LocalClientCommand> = listOf(
    HelpClientCommand(),
    HistoryClientCommand(),
    ExitClientCommand(),
    ExecuteScriptClientCommand(runLine),
)

fun buildNetworkCommands(): List<NetworkClientCommand> = listOf(
    InfoClientCommand(),
    ShowClientCommand(),
    AddClientCommand(),
    UpdateIdClientCommand(),
    RemoveByIdClientCommand(),
    ClearClientCommand(),
    RemoveFirstClientCommand(),
    RemoveLowerClientCommand(),
    FilterContainsNameClientCommand(),
    FilterLessThanTypeClientCommand(),
    PrintDescendingClientCommand(),
)
