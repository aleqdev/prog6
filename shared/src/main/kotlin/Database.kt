package org.example

import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * Интерфейс сущности БД
 */
interface Entity

/**
 * Интерфейс коллекции сущностей
 */
interface EntityCollection<E : Entity, D : Database<D>> {
    fun add(entity: E)
    fun remove(predicate: Predicate<E>)
    fun update(predicate: Predicate<E>, updateFn: (E) -> E)
    fun rules(): List<DatabaseEntityRule<E, D>>
    fun prepopulate(): List<DatabaseEntityPrepopulate<E, D>>
    fun asList(): List<E>
}

/**
 * Интерфейс базы данных
 */
interface Database<D : Database<D>> {
    fun <E : Entity> createCollection(
        kls: KClass<E>,
        rules: List<DatabaseEntityRule<E, D>>,
        prepopulate: List<DatabaseEntityPrepopulate<E, D>>
    )

    fun <E : Entity> query(kls: KClass<E>): List<E>?
    fun <E : Entity> add(entity: E, kls: KClass<E>)
    fun <E : Entity> remove(predicate: Predicate<E>, kls: KClass<E>)
    fun <E : Entity> update(predicate: Predicate<E>, updateFn: (E) -> E, kls: KClass<E>)
}

/**
 * Интерфейс для правил валидации сущности
 */
fun interface DatabaseEntityRule<E, D : Database<D>> {
    fun check(entity: E, db: D, errors: DatabaseEntityRuleErrorCollector)
}

/**
 * Интерфейс для предзаполнения сущности
 */
fun interface DatabaseEntityPrepopulate<E, D : Database<D>> {
    fun prepopulate(entity: E, db: D): E
}

/**
 * Сборщик ошибок
 */
interface DatabaseEntityRuleErrorCollector {
    fun fail(message: String)
    fun asList(): List<String>
}

/**
 * Сборщик ошибок списком
 */
class ListDatabaseEntityRuleErrorCollector : DatabaseEntityRuleErrorCollector {
    private val items: MutableList<String> = mutableListOf()

    override fun fail(message: String) {
        items.add(message)
    }

    override fun asList(): List<String> = items
}

/**
 * Реализация базы данных
 */
class ArrayDequeueDatabase : Database<ArrayDequeueDatabase> {
    private val collections: MutableMap<KClass<*>, EntityCollection<*, ArrayDequeueDatabase>> = mutableMapOf()

    override fun <E : Entity> query(kls: KClass<E>): List<E>? {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        return collection?.asList()
    }

    override fun <E : Entity> createCollection(
        kls: KClass<E>,
        rules: List<DatabaseEntityRule<E, ArrayDequeueDatabase>>,
        prepopulate: List<DatabaseEntityPrepopulate<E, ArrayDequeueDatabase>>
    ) {
        collections[kls] = object : EntityCollection<E, ArrayDequeueDatabase> {
            private var items: ArrayDeque<E> = ArrayDeque()

            override fun add(entity: E) {
                var entity = entity

                // Авто поля
                for (pre in prepopulate()) {
                    entity = pre.prepopulate(entity, this@ArrayDequeueDatabase)
                }

                // Валидация
                val errors = ListDatabaseEntityRuleErrorCollector()
                for (rule in rules()) {
                    rule.check(entity, this@ArrayDequeueDatabase, errors)
                }

                if (errors.asList().isNotEmpty()) {
                    errors.asList().forEach { println("Ошибка валидации: $it") }
                    return
                }

                items.addLast(entity)
            }

            override fun remove(predicate: Predicate<E>) {
                items.removeIf(predicate)
            }

            override fun update(predicate: Predicate<E>, updateFn: (E) -> E) {
                items.replaceAll { item ->
                    if (predicate.test(item)) updateFn(item) else item
                }
            }

            override fun rules(): List<DatabaseEntityRule<E, ArrayDequeueDatabase>> = rules
            override fun prepopulate(): List<DatabaseEntityPrepopulate<E, ArrayDequeueDatabase>> = prepopulate
            override fun asList(): List<E> = items.toList()
        }
    }

    override fun <E : Entity> add(entity: E, kls: KClass<E>) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.add(entity)
    }

    override fun <E : Entity> remove(predicate: Predicate<E>, kls: KClass<E>) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.remove(predicate)
    }

    override fun <E : Entity> update(
        predicate: Predicate<E>,
        updateFn: (E) -> E,
        kls: KClass<E>
    ) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.update(predicate, updateFn)
    }

    // Inline-обёртки для удобства
    inline fun <reified E : Entity> createCollection(
        rules: List<DatabaseEntityRule<E, ArrayDequeueDatabase>>,
        prepopulate: List<DatabaseEntityPrepopulate<E, ArrayDequeueDatabase>> = listOf()
    ) = createCollection(E::class, rules, prepopulate)

    inline fun <reified E : Entity> query(): List<E>? = query(E::class)
    inline fun <reified E : Entity> add(entity: E) = add(entity, E::class)
    inline fun <reified E : Entity> remove(predicate: Predicate<E>) = remove(predicate, E::class)
    inline fun <reified E : Entity> update(predicate: Predicate<E>, noinline updateFn: (E) -> E) =
        update(predicate, updateFn, E::class)
}
