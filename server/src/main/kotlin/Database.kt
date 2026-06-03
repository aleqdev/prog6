package org.example

import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * Интерфейс коллекции сущностей
 */
interface EntityCollection<E : Any, D : Database<D>> {
    fun add(entity: E)
    fun addNonPopulated(entity: E)
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
    fun <E : Any> createCollection(
        kls: KClass<E>,
        rules: List<DatabaseEntityRule<E, D>>,
        prepopulate: List<DatabaseEntityPrepopulate<E, D>>
    )

    fun <E : Any> query(kls: KClass<E>): List<E>?
    fun <E : Any> add(entity: E, kls: KClass<E>)
    fun <E : Any> addNonPopulated(entity: E, kls: KClass<E>)
    fun <E : Any> remove(predicate: Predicate<E>, kls: KClass<E>)
    fun <E : Any> update(predicate: Predicate<E>, updateFn: (E) -> E, kls: KClass<E>)
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

    override fun <E : Any> query(kls: KClass<E>): List<E>? {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        return collection?.asList()
    }

    override fun <E : Any> createCollection(
        kls: KClass<E>,
        rules: List<DatabaseEntityRule<E, ArrayDequeueDatabase>>,
        prepopulate: List<DatabaseEntityPrepopulate<E, ArrayDequeueDatabase>>
    ) {
        collections[kls] = object : EntityCollection<E, ArrayDequeueDatabase> {
            private var items: ArrayDeque<E> = ArrayDeque()

            override fun add(entity: E) {
                var prepopulated = entity
                for (pre in prepopulate()) {
                    prepopulated = pre.prepopulate(prepopulated, this@ArrayDequeueDatabase)
                }
                addNonPopulated(prepopulated)
            }

            override fun addNonPopulated(entity: E) {
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

    override fun <E : Any> add(entity: E, kls: KClass<E>) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.add(entity)
    }

    override fun <E : Any> addNonPopulated(entity: E, kls: KClass<E>) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.addNonPopulated(entity)
    }

    override fun <E : Any> remove(predicate: Predicate<E>, kls: KClass<E>) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.remove(predicate)
    }

    override fun <E : Any> update(
        predicate: Predicate<E>,
        updateFn: (E) -> E,
        kls: KClass<E>
    ) {
        @Suppress("UNCHECKED_CAST")
        val collection = collections[kls] as? EntityCollection<E, ArrayDequeueDatabase>
        collection?.update(predicate, updateFn)
    }

    // Inline-обёртки для удобства
    inline fun <reified E : Any> createCollection(
        rules: List<DatabaseEntityRule<E, ArrayDequeueDatabase>>,
        prepopulate: List<DatabaseEntityPrepopulate<E, ArrayDequeueDatabase>> = listOf()
    ) = createCollection(E::class, rules, prepopulate)

    inline fun <reified E : Any> query(): List<E>? = query(E::class)
    inline fun <reified E : Any> add(entity: E) = add(entity, E::class)
    inline fun <reified E : Any> addNonPopulated(entity: E) = addNonPopulated(entity, E::class)
    inline fun <reified E : Any> remove(predicate: Predicate<E>) = remove(predicate, E::class)
    inline fun <reified E : Any> update(predicate: Predicate<E>, noinline updateFn: (E) -> E) =
        update(predicate, updateFn, E::class)
}
