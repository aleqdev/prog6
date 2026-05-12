package org.example

import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.Reader
import java.io.Writer
import tools.jackson.module.kotlin.readValue


val xmlMapper: XmlMapper = XmlMapper.builder()
    .addModule(kotlinModule())
    .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    .configure(SerializationFeature.INDENT_OUTPUT, true)
    .build()

/**
 * Сериализация билетов
 */
fun serializeTickets(tickets: List<Ticket>, writer: Writer) {
    xmlMapper.writeValue(writer, tickets)
}

/**
 * Десериализация билетов
 */
fun deserializeTickets(reader: Reader): List<Ticket> {
    return xmlMapper.readValue<List<Ticket>>(reader)
}
