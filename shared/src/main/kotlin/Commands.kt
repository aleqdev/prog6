package org.example

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Базовый класс команды передаваемой между клиентом и сервером.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes(
    JsonSubTypes.Type(value = InfoCommand::class, name = "info"),
    JsonSubTypes.Type(value = ShowCommand::class, name = "show"),
    JsonSubTypes.Type(value = AddCommand::class, name = "add"),
    JsonSubTypes.Type(value = UpdateIdCommand::class, name = "update_id"),
    JsonSubTypes.Type(value = RemoveByIdCommand::class, name = "remove_by_id"),
    JsonSubTypes.Type(value = ClearCommand::class, name = "clear"),
    JsonSubTypes.Type(value = RemoveFirstCommand::class, name = "remove_first"),
    JsonSubTypes.Type(value = RemoveLowerCommand::class, name = "remove_lower"),
    JsonSubTypes.Type(value = FilterContainsNameCommand::class, name = "filter_contains_name"),
    JsonSubTypes.Type(value = FilterLessThanTypeCommand::class, name = "filter_less_than_type"),
    JsonSubTypes.Type(value = PrintDescendingCommand::class, name = "print_descending"),
    JsonSubTypes.Type(value = DisconnectCommand::class, name = "(disconnect)"),
)
sealed class Command {
    abstract fun name(): String
}

class InfoCommand : Command() {
    override fun name() = "info"
}

class ShowCommand : Command() {
    override fun name() = "show"
}

data class AddCommand(val ticket: Ticket) : Command() {
    override fun name() = "add"
}

data class UpdateIdCommand(val id: Long, val ticket: Ticket) : Command() {
    override fun name() = "update_id"
}

data class RemoveByIdCommand(val id: Long) : Command() {
    override fun name() = "remove_by_id"
}

class ClearCommand : Command() {
    override fun name() = "clear"
}

class RemoveFirstCommand : Command() {
    override fun name() = "remove_first"
}

data class RemoveLowerCommand(val ticket: Ticket) : Command() {
    override fun name() = "remove_lower"
}

data class FilterContainsNameCommand(val substring: String) : Command() {
    override fun name() = "filter_contains_name"
}

data class FilterLessThanTypeCommand(val type: TicketType) : Command() {
    override fun name() = "filter_less_than_type"
}

class PrintDescendingCommand : Command() {
    override fun name() = "print_descending"
}

/**
 * Сигнал об отключении клиента. Не вызывается пользователем
 */
class DisconnectCommand : Command() {
    override fun name() = "(disconnect)"
}
