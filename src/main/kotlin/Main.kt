package org.example

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