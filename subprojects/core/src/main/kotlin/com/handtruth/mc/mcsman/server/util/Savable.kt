package com.handtruth.mc.mcsman.server.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface Savable {
    val stateProperties: MutableCollection<Property<*>>
    val state: State

    operator fun <T> Pair<KSerializer<T>, () -> T>.provideDelegate(
        thisRef: Savable,
        property: KProperty<*>
    ): Property<T> {
        val name = property.name
        val initial = state.loadOrNull(name, first) ?: second()
        val prop = Property(name, initial, first)
        stateProperties += prop
        return prop
    }

    operator fun <T : Any> KSerializer<T>.provideDelegate(
        thisRef: Savable,
        property: KProperty<*>
    ): Property<T> {
        val name = property.name
        val initial = state.load(name, this)
        val prop = Property(name, initial, this)
        stateProperties += prop
        return prop
    }

    class Property<T>(
        val name: String, private var field: T,
        val serializer: KSerializer<T>
    ) : ReadWriteProperty<Savable, T> {
        override fun getValue(thisRef: Savable, property: KProperty<*>) = field
        override fun setValue(thisRef: Savable, property: KProperty<*>, value: T) {
            field = value
        }

        fun save(state: SaveState) {
            state.save(name, serializer, field)
        }
    }
}

inline fun <reified T : Any> Savable.state(
    serializer: KSerializer<T> = serializer(),
    noinline default: () -> T
): Pair<KSerializer<T>, () -> T> {
    contract {
        callsInPlace(default, InvocationKind.AT_MOST_ONCE)
    }
    return serializer to default
}

inline fun <reified T : Any> Savable.state(
    name: String,
    serializer: KSerializer<T> = serializer(),
    default: () -> T
): Savable.Property<T> {
    contract {
        callsInPlace(default, InvocationKind.AT_MOST_ONCE)
    }
    val initial = state.loadOrNull(name, serializer) ?: default()
    val prop = Savable.Property(name, initial, serializer)
    stateProperties += prop
    return prop
}

inline fun <reified T : Any> Savable.state(serializer: KSerializer<T> = serializer()): KSerializer<T> {
    return serializer
}

inline fun <reified T : Any> Savable.state(
    name: String, serializer: KSerializer<T> = serializer()
): Savable.Property<T> {
    val initial = state.load(name, serializer)
    val prop = Savable.Property(name, initial, serializer)
    stateProperties += prop
    return prop
}

fun Savable.save() {
    for (property in stateProperties)
        property.save(state)
}
