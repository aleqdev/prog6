package org.example

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Scanner
import kotlin.reflect.full.createInstance

/**
 * Аннотация для полей, которые не должны вводиться пользователем.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FromTerminalIgnore

/**
 * Правило для валидатора
 */
interface ValidatorRule<T> {
    fun isValid(value: T): Boolean
}

/**
 * Валидатор
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValidateWith(val rule: KClass<out ValidatorRule<*>>)

/**
 * Утилита для построения объектов из терминального ввода.
 */
object TerminalBuilder {

    /**
     * Построение экземпляра класса через ввод.
     */
    fun <T : Any> build(clazz: KClass<T>, scanner: Scanner = Scanner(System.`in`)): T? {
        val constructor = clazz.primaryConstructor ?: return null
        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            // Пропускаем автогенерируемые поля
            if (param.findAnnotation<FromTerminalIgnore>() != null) continue

            val type = param.type.classifier as? KClass<*> ?: continue

            val validator = param.findAnnotation<ValidateWith>()

            while (true) {
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

                if (validator != null) {
                    val validatorInstance = validator.rule.createInstance() as ValidatorRule<Any?>
                    if (!validatorInstance.isValid(args[param])) {
                        println("Невалидное значение")
                        continue
                    }
                }
                break
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
                    println("Поле не может быть пустым. Попробуйте снова.")
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
                println("Некорректный формат даты. Пример: 12.12.2012 12:12:12 Z")
                continue
            }
        }
    }

    private fun enterEnum(name: String?, type: KClass<*>, scanner: Scanner): Any? {
        val constants = type.java.enumConstants
        println("Доступные значения для '${name}': ${constants.joinToString(", ")}")

        while (true) {
            print("Введите значение: ")
            val input = scanner.nextLine().trim()
            if (input.isEmpty()) {
                return null
            }
            val found = constants.find { it.toString().equals(input, ignoreCase = true) }
            if (found != null) return found
            println("Значение не найдено. Попробуйте снова.")
        }
    }
}