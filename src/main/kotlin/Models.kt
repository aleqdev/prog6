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
    val name: String? = null,
    val coordinates: Coordinates? = Coordinates(),
    @FromTerminalIgnore
    val creationDate: ZonedDateTime? = null,
    val price: Long = 0,
    val refundable: Boolean = false,
    val type: TicketType? = null,
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
    val x: Float? = null,
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
    val name: String? = null,
    val date: ZonedDateTime? = null,
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
