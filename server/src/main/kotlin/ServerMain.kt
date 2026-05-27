package org.example

/**
 * Точка входа сервера.
 */
fun main() {
    val fileName = System.getenv("DB_FILENAME")
    if (!fileName.isNullOrEmpty()) {
        startServer(fileName)
    } else {
        println("Переменная окружения DB_FILENAME не установлена")
    }
}
