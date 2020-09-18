package com.handtruth.mc.mcsman.server.util

import com.epublica.java.WeakValueHashMap
import com.handtruth.kommon.Log
import com.handtruth.kommon.concurrent.Later
import com.handtruth.kommon.concurrent.emptyLater
import com.handtruth.kommon.concurrent.later
import com.handtruth.kommon.concurrent.laterOf
import com.handtruth.kommon.default
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.event.checkReactorContext
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.koin.core.inject
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.primaryConstructor

private val allRowVar: ThreadLocal<ResultRow> = ThreadLocal()
val allRow: ResultRow
    get() = allRowVar.get() ?: error("NO ROW SUPPLIED!!!")

abstract class Shadow<S : Shadow<S, K>, K : Comparable<K>> {
    private val _properties: MutableSet<Property<*>> = mutableSetOf()
    private val properties: Set<Property<*>> get() = _properties

    protected val node = DestroyableNode()

    val isDeleted get() = node.isDestroyed

    @Suppress("UNCHECKED_CAST")
    open val controller: Controller<S, K> by lazy {
        this@Shadow::class.companionObjectInstance as Controller<S, K>
    }

    internal fun invokeUpdate() {
        properties.forEach { it.update() }
        onUpdate()
    }

    internal suspend fun invokeLoad() {
        coroutineScope {
            properties.forEach { launch { it.load() } }
        }
        onLoad()
    }

    val id: K by lazy { allRow[controller.key].value }

    operator fun <T> Expression<T>.provideDelegate(thisRef: Shadow<S, K>, property: KProperty<*>): ColumnProperty<T> {
        val p = ColumnProperty(this)
        _properties += p
        return p
    }

    @OptIn(ReactorContext::class)
    suspend fun update() {
        node.invoke {
            @Suppress("UNCHECKED_CAST")
            controller.update(this as S)
        }
    }

    @ReactorContext
    suspend fun delete() {
        node.invoke {
            @Suppress("UNCHECKED_CAST")
            controller.delete(this as S)
        }
    }

    protected open fun onUpdate() {

    }

    protected open suspend fun onLoad() {

    }

    @ReactorContext
    protected open suspend fun onDelete() {

    }

    protected open fun onDispose() {

    }

    internal fun invokeDispose() {
        onDispose()
        for (property in properties) {
            property.dispose()
        }
        node.destroy()
    }

    abstract inner class Property<R> : ReadOnlyProperty<Shadow<S, K>, R> {
        open fun update() {}
        open suspend fun load() {}
        open fun dispose() {}
    }

    inner class ColumnProperty<R>(val column: Expression<R>) : Property<R>() {
        private var value = allRow[column]
        override fun update() {
            value = allRow[column]
        }

        override operator fun getValue(thisRef: Shadow<S, K>, property: KProperty<*>): R = node.invoke { value }
    }

    inner class OptionalReferenceProperty<R : Shadow<R, F>, F : Comparable<F>>(
        val controller: Controller<R, F>,
        val fk: Expression<EntityID<F>?>
    ) : Property<Later<R?>>() {
        private var idValue = allRow[fk]
        private var field: Later<R?> = createLater(idValue)

        private fun createLater(id: EntityID<F>?): Later<R?> = if (id == null)
            laterOf(null)
        else
            later { controller.get(id.value) }

        override fun update() {
            idValue = allRow[fk]
        }

        override suspend fun load() {
            field.get()
        }

        override operator fun getValue(thisRef: Shadow<S, K>, property: KProperty<*>): Later<R?> = node.invoke { field }

        override fun dispose() {
            idValue = null
            field = emptyLater()
        }
    }

    inner class ReferenceProperty<R : Shadow<R, F>, F : Comparable<F>>(
        val controller: Controller<R, F>,
        val fk: Expression<EntityID<F>>
    ) : Property<Later<R>>() {
        private var idValue = allRow[fk]
        private var field: Later<R> = createLater(idValue)

        private fun createLater(id: EntityID<F>): Later<R> = later { controller.get(id.value) }

        override fun update() {
            idValue = allRow[fk]
        }

        override suspend fun load() {
            field.get()
        }

        override operator fun getValue(thisRef: Shadow<S, K>, property: KProperty<*>): Later<R> = node.invoke { field }

        override fun dispose() {
            field = emptyLater()
        }
    }

    @JvmName("optWith")
    protected infix fun <R : Shadow<R, F>, F : Comparable<F>> Controller<R, F>.with(
        fk: Expression<EntityID<F>?>
    ): OptionalReferenceProperty<R, F> {
        val property = OptionalReferenceProperty(this, fk)
        _properties += property
        return property
    }

    protected infix fun <R : Shadow<R, F>, F : Comparable<F>> Controller<R, F>.with(
        fk: Expression<EntityID<F>>
    ): ReferenceProperty<R, F> {
        val property = ReferenceProperty(this, fk)
        _properties += property
        return property
    }

    abstract class Controller<S : Shadow<S, K>, K : Comparable<K>> : TaskBranch {

        abstract val key: Expression<EntityID<K>>

        val db: Database by inject()

        private val cache = Collections.synchronizedMap(WeakValueHashMap<K, S>())

        private val mutex = Mutex()

        protected abstract suspend fun fetch(key: K): ResultRow

        suspend fun get(key: K): S = mutex.withLock {
            cache.getOrPut(key) {
                withContext(allRowVar.asContextElement(fetch(key))) {
                    createInstance()
                }
            }
        }

        fun getCached(key: K): S? = cache[key]

        private suspend fun exec(query: Query, from: Database) = suspendTransaction(from) {
            query.toList()
        }

        suspend fun loadAll(query: Query): List<S> = mutex.withLock {
            val result = mutableListOf<S>()
            for (row in exec(query, db)) {
                val k = row[key].value
                val info = cache[k]
                result += withContext(allRowVar.asContextElement(row)) {
                    if (info == null) {
                        val shadow = createInstance()
                        cache[k] = shadow
                        shadow
                    } else {
                        info.invokeUpdate()
                        info
                    }
                }
            }
            return result
        }

        suspend fun findOne(query: Query): S? {
            return loadAll(query.limit(1)).firstOrNull()
        }

        @ReactorContext
        internal suspend fun update(shadow: S) {
            checkReactorContext()
            withContext(allRowVar.asContextElement(fetch(shadow.id))) {
                shadow.invokeUpdate()
            }
        }

        @ReactorContext
        internal suspend fun delete(shadow: S) {
            checkReactorContext()
            shadow.onDelete()
            shadow.invokeDispose()
        }

        private suspend inline fun createInstance(): S = spawn().also { it.id }

        @Suppress("UNCHECKED_CAST")
        protected open suspend fun spawn(): S {
            val j = this::class.java
            val name = j.canonicalName!!
            val i = name.lastIndexOf('.')
            assert(i != -1)
            val entityName = name.substring(0, i)
            return j.classLoader.loadClass(entityName).kotlin.primaryConstructor!!.call() as S
        }
    }

    protected fun finalize() {
        Log.default("DISPOSER").debug { "DISPOSE: ${this@Shadow}" }
        invokeDispose()
    }
}

abstract class IntIdShadow<S : IntIdShadow<S>> : Shadow<S, Int>() {

    override val controller: IntIdController<S> get() = super.controller as IntIdController<S>

    @ReactorContext
    override suspend fun onDelete() {
        suspendTransaction(controller.db) {
            val table = controller.table
            table.deleteWhere { table.id eq this@IntIdShadow.id }
        }
        super.onDelete()
    }

    abstract class IntIdController<S : IntIdShadow<S>>(val table: IdTable<Int>) : Controller<S, Int>() {

        override val key = table.id

        override suspend fun fetch(key: Int): ResultRow {
            return suspendTransaction(db) {
                table.select { table.id eq key }.first()
            }
        }

        suspend fun getOrNull(key: Int): S? = if (key == 0) null else get(key)
    }
}

interface NamedShadowsController<S : Shadow<S, K>, K : Comparable<K>> {
    suspend fun getOrNull(name: String): S?
    suspend fun get(name: String) = getOrNull(name) ?: throw NotExistsMCSManException("no object with name \"$name\"")
}
