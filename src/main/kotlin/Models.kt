package org.example

import com.fasterxml.jackson.annotation.JsonRootName
import java.time.ZonedDateTime

/**
 * Билет на мероприятие.
 */
@JsonRootName("Ticket")
data class Ticket(
    @FromTerminalIgnore
    val id: Long = 0,
    @ValidateWith(NotNull::class)
    val name: String? = null,
    @ValidateWith(NotNull::class)
    val coordinates: Coordinates? = Coordinates(),
    @FromTerminalIgnore
    val creationDate: ZonedDateTime? = null,
    @ValidateWith(IsPositive::class)
    val price: Long = 0,
    val refundable: Boolean = false,
    val type: TicketType? = null,
    @ValidateWith(NotNull::class)
    val event: Event? = Event()
) : Entity, Comparable<Ticket> {

    override fun compareTo(other: Ticket): Int = this.id.compareTo(other.id)

    override fun toString(): String {
        return "Ticket{id=$id, name='$name', coordinates=$coordinates, creationDate=$creationDate, price=$price, refundable=$refundable, type=$type, event=$event}"
    }
}

/**
 * Координаты места проведения мероприятия.
 */
data class Coordinates(
    @ValidateWith(NotNull::class)
    val x: Float? = null,
    @ValidateWith(CoordinateYMin::class)
    val y: Double? = null
) : Entity, Comparable<Coordinates> {

    override fun compareTo(other: Coordinates): Int {
        val thisSum = (this.x?.toDouble() ?: 0.0) + (this.y ?: 0.0)
        val otherSum = (other.x?.toDouble() ?: 0.0) + (other.y ?: 0.0)
        return thisSum.compareTo(otherSum)
    }

    override fun toString(): String = "Coordinates{x=$x, y=$y}"
}

/**
 * Событие
 */
data class Event(
    @FromTerminalIgnore
    val id: Long = 0,
    @ValidateWith(NotNull::class)
    val name: String? = null,
    @ValidateWith(NotNull::class)
    val date: ZonedDateTime? = null,
    @ValidateWith(NotNull::class)
    val eventType: EventType? = null
) : Entity, Comparable<Event> {

    override fun compareTo(other: Event): Int = this.id.compareTo(other.id)

    override fun toString(): String = "Event{id=$id, name='$name', date=$date, eventType=$eventType}"
}

/**
 * Типы билетов
 */
enum class TicketType : Comparable<TicketType> {
    VIP, USUAL, BUDGETARY, CHEAP;
}

/**
 * Типы мероприятий.
 */
enum class EventType {
    CONCERT, BASEBALL, OPERA
}

/**
 * Валидатор
 */
class IsPositive : ValidatorRule<Long> {
    override fun isValid(value: Long): Boolean = value > 0
}

/**
 * Валидатор
 */
class NotNull : ValidatorRule<Any?> {
    override fun isValid(value: Any?): Boolean = value != null
}

/**
 * Валидатор
 */
class CoordinateYMin : ValidatorRule<Double?> {
    override fun isValid(value: Double?): Boolean {
        if (value == null) return false
        return value > -231.0
    }
}

