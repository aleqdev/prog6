package org.example

object AppLogger {
    private var mode: String = "JUL"

    fun setLogger(type: String) {
        mode = type
    }

    fun log(message: String) {
        when (mode) {
            "Logback" -> {
                org.slf4j.LoggerFactory.getLogger("Logback").info(message)
            }
            "Log4J2" -> {
                org.apache.logging.log4j.LogManager.getLogger("Log4J2").info(message)
            }
            "JUL" -> {
                java.util.logging.Logger.getLogger("JUL").info(message)
            }
            else -> println("Неизвестный логгер: $message")
        }
    }
}
