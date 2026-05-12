package org.example

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Scanner

/**
 * Аннотация для полей, которые не должны вводиться пользователем.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FromTerminalIgnore

/**
 * Утилита для построения объектов из терминального ввода.
 */
object TerminalBuilder {

    /**
     * Построение экземпляра класса через ввод.
     * @param clazz класс для создания
     * @param scanner источник ввода
     * @return созданный объект или null при ошибке
     */
    fun <T : Any> build(clazz: KClass<T>, scanner: Scanner = Scanner(System.`in`)): T? {
        val constructor = clazz.primaryConstructor ?: return null
        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            // Пропускаем автогенерируемые поля
            if (param.findAnnotation<FromTerminalIgnore>() != null) continue

            val type = param.type.classifier as? KClass<*> ?: continue

            when (type) {
                Int::class -> args[param] = enterInt(param.name, scanner)
                Long::class -> args[param] = enterLong(param.name, scanner)
                Float::class -> args[param] = enterFloat(param.name, scanner)
                Double::class -> args[param] = enterDouble(param.name, scanner)
                String::class -> args[param] = enterString(param.name, scanner, param.type.isMarkedNullable)
                Boolean::class -> args[param] = enterBoolean(param.name, scanner)
                ZonedDateTime::class -> args[param] = enterZonedDateTime(param.name, scanner)
                else -> {
                    when {
                        type.java.isEnum -> args[param] = enterEnum(param.name, type, scanner)
                        else -> {
                            println("Введите данные для поля '${param.name}':")
                            args[param] = build(type, scanner)
                        }
                    }
                }
            }
        }

        return try {
            constructor.callBy(args)
        } catch (e: Exception) {
            println("Ошибка создания объекта: ${e.message}")
            null
        }
    }

    private fun enterString(name: String?, scanner: Scanner, nullable: Boolean): String? {
        while (true) {
            print("Введите ${name ?: "значение"} (String): ")
            val input = scanner.nextLine()
            if (input.isEmpty()) {
                return if (nullable) null else {
                    println("❌ Поле не может быть пустым. Попробуйте снова.")
                    continue
                }
            }
            return input
        }
    }

    private fun enterBoolean(name: String?, scanner: Scanner): Boolean {
        while (true) {
            print("Введите ${name ?: "значение"} (д/н или y/n): ")
            return when (scanner.nextLine().trim().lowercase()) {
                "д", "y", "yes", "да" -> true
                "н", "n", "no", "нет" -> false
                else -> {
                    println("Некорректный ввод. Используйте: д/н или y/n")
                    continue
                }
            }
        }
    }

    private fun enterInt(name: String?, scanner: Scanner): Int {
        while (true) {
            print("Введите ${name ?: "значение"} (целое число): ")
            return scanner.nextLine().trim().toIntOrNull()?.also { return it }
                ?: run { println("Введите корректное целое число"); continue }
        }
    }

    private fun enterLong(name: String?, scanner: Scanner): Long {
        while (true) {
            print("Введите ${name ?: "значение"} (число): ")
            return scanner.nextLine().trim().toLongOrNull()?.also { return it }
                ?: run { println("Введите корректное число"); continue }
        }
    }

    private fun enterFloat(name: String?, scanner: Scanner): Float {
        while (true) {
            print("Введите ${name ?: "значение"} (дробное число): ")
            return scanner.nextLine().trim().toFloatOrNull()?.also { return it }
                ?: run { println("Введите корректное дробное число"); continue }
        }
    }

    private fun enterDouble(name: String?, scanner: Scanner): Double {
        while (true) {
            print("Введите ${name ?: "значение"} (дробное число): ")
            return scanner.nextLine().trim().toDoubleOrNull()?.also { return it }
                ?: run { println("Введите корректное дробное число"); continue }
        }
    }

    private fun enterZonedDateTime(name: String?, scanner: Scanner): ZonedDateTime {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss X")
        while (true) {
            print("Введите ${name ?: "дату"} (формат: дд.ММ.гггг ЧЧ:мм:сс Z): ")
            return try {
                ZonedDateTime.parse(scanner.nextLine().trim(), formatter)
            } catch (e: DateTimeParseException) {
                println("Некорректный формат даты. Пример: 25.12.2024 18:30:00 +03")
                continue
            }
        }
    }

    private fun enterEnum(name: String?, type: KClass<*>, scanner: Scanner): Any {
        val constants = type.java.enumConstants
        println("Доступные значения для '${name}': ${constants.joinToString(", ")}")

        while (true) {
            print("Введите значение: ")
            val input = scanner.nextLine().trim()
            val found = constants.find { it.toString().equals(input, ignoreCase = true) }
            if (found != null) return found
            println("Значение не найдено. Попробуйте снова.")
        }
    }
}