package com.handtruth.mc.mcsman.common.event

import com.handtruth.kommon.BeanJar
import com.handtruth.mc.mcsman.MCSManException
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.event.EventProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.serializer
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses

/**
 * Turns data class to MCSMan event definition.
 *
 * This annotation instructs MCSMan annotation processor to generate shadow implementation for interface and external
 * serializer for event class. Then annotation class can be registered using [EventsBase] class instance. Any event
 * interface or class must inherit [Event] interface.
 *
 * @property name unique protocol and table name for annotated event
 *
 * @see EventsBase
 * @see Event
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@SerialInfo
annotation class MCSManEvent(val name: String) {
    /**
     * Generate event tables for the specified classes.
     *
     * @see com.handtruth.mc.mcsman.event.Event
     */
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.CLASS)
    @MustBeDocumented
    annotation class Table(vararg val events: KClass<out Event>)

    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.CLASS)
    @MustBeDocumented
    annotation class Register(vararg val events: KClass<out Event>)
}

enum class EventTypes {
    Root, Interface, Class
}

sealed class EventInfo(
    val type: EventTypes,
    val name: String,
    val interfaces: List<EventInfo>
) : BeanJar.Simple() {
    class Simple(
        type: EventTypes,
        name: String,
        interfaces: List<EventInfo>
    ) : EventInfo(type, name, interfaces)
    class Full(
        type: EventTypes,
        name: String,
        interfaces: List<EventInfo>,
        val `class`: KClass<out Event>,
        val serializer: KSerializer<out Event>
    ) : EventInfo(type, name, interfaces)
}

abstract class EventsBase {

    companion object {
        val root = EventInfo.Full(EventTypes.Root, "", emptyList(), Event::class, EventProjection.serializer())
    }

    private val eventsByClass: MutableMap<KClass<out Event>, EventInfo.Full> = hashMapOf(Event::class to root)
    private val eventsByName: MutableMap<String, EventInfo> = hashMapOf("" to root)

    private fun add(eventInfo: EventInfo.Full) {
        eventsByName[eventInfo.name] = eventInfo
        eventsByClass[eventInfo.`class`] = eventInfo
    }

    private fun KClass<out Event>.getEventInterfaces(): Set<KClass<out Event>> {
        @Suppress("UNCHECKED_CAST")
        val supers = superclasses.asSequence()
            .filter { it.java.isInterface && it.isSubclassOf(Event::class) }
            .map { it as KClass<out Event> }
        val result = supers.toMutableSet()
        supers.forEach { result += it.getEventInterfaces() }
        return result
    }

    private fun registerUnchecked(`class`: KClass<out Event>): EventInfo.Full {
        eventsByClass[`class`]?.let { return it }
        val interfaces = `class`.getEventInterfaces().mapNotNull {
            if (`class`.hasAnnotation<MCSManEvent>()) register(it) else null
        }
        val name = `class`.findAnnotation<MCSManEvent>()!!.name
        val type = when {
            `class`.java.isInterface -> EventTypes.Interface
            else -> EventTypes.Class
        }

        @Suppress("UNCHECKED_CAST")
        val serializer = when (type) {
            EventTypes.Root -> throw Error("unreachable")
            EventTypes.Interface -> `class`.java.classLoader
                .loadClass("${`class`.java.canonicalName}Projection").kotlin.serializer() as KSerializer<out Event>
            EventTypes.Class -> `class`.java.classLoader.loadClass("${`class`.java.canonicalName}Serializer")
                .kotlin.objectInstance as KSerializer<out Event>
        }
        return EventInfo.Full(
            type, name, interfaces, `class`, serializer
        ).also(this::add)
    }

    open fun register(`class`: KClass<out Event>): EventInfo.Full {
        val annotation = `class`.findAnnotation<MCSManEvent>() ?: throw NotMCSManEventException(`class`)
        eventsByName[annotation.name]?.let { info ->
            if (info is EventInfo.Full && info.`class` != `class`)
                throw AlreadyRegisteredEventException("event with name \"${annotation.name}\" already registered")
        }
        return registerUnchecked(`class`)
    }

    inline fun <reified E : Event> register() = register(E::class)

    protected fun simple(className: String, interfaces: List<String>, type: EventTypes): EventInfo {
        val existingInfo = describeOrNull(className)
        return if (existingInfo != null) {
            if (existingInfo is EventInfo.Simple) {
                val list = interfaces.map { simple(it, mutableListOf(), EventTypes.Interface) }
                val oldList = existingInfo.interfaces as MutableList<EventInfo>
                oldList.clear()
                oldList += list
            }
            existingInfo
        } else {
            val list = interfaces.map { simple(it, mutableListOf(), EventTypes.Interface) }
            val info = EventInfo.Simple(type, className, list)
            eventsByName[className] = info
            info
        }
    }

    fun describeOrNull(name: String) = eventsByName[name]
    fun describe(name: String) = describeOrNull(name) ?: throw NotRegisteredEventException(name)

    fun describeOrNull(`class`: KClass<out Event>) = eventsByClass[`class`]
    fun describe(`class`: KClass<out Event>) = describeOrNull(`class`)
        ?: throw NotRegisteredEventException(`class`.toString())

    inline fun <reified E : Event> describeOrNull() = describeOrNull(E::class)
    inline fun <reified E : Event> describe() = describe(E::class)

    fun describeOrNull(event: Event) = describeOrNull(event::class)
    fun describe(event: Event) = describe(event::class)

    val all = eventsByName.values.asSequence()
    val count = eventsByName.size

    abstract val bus: Flow<Event>
    abstract suspend fun raise(event: Event)
}

sealed class EventException(message: String) : MCSManException(message)
class AlreadyRegisteredEventException(val name: String) : EventException("Event with name \"$name\" already registered")
class NotFoundEventException(message: String) : EventException(message)
class NotRegisteredEventException(name: String) : EventException("Event \"$name\" is not registered")
class NotMCSManEventException(`class`: KClass<out Event>) : EventException("$`class` is not a MCSMan event")
