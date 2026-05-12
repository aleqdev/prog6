package org.example

import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.ZonedDateTime
import java.util.Scanner

/**
 * Точка входа в приложение.
 */
fun main(args: Array<String>) {
    if (args.contains("--server")) {
        val fileName = System.getenv("DB_FILENAME")
        if (!fileName.isNullOrEmpty()) {
            startServer(fileName)
        } else {
            println("Переменная окружения DB_FILENAME не установлена")
        }
    } else {
        startClient()
    }
}