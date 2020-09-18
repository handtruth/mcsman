@file:Suppress("FunctionName")

package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.nbt.*
import com.handtruth.mc.nbt.tags.CompoundTag
import kotlinx.io.ByteArrayInput
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

interface SaveState {
    fun <T> save(qualifier: String, serializer: SerializationStrategy<T>, value: T)

    fun toByteArray(): ByteArray
}

inline fun <reified T : Any> SaveState.save(qualifier: String, value: T) {
    save(qualifier, serializer(), value)
}

inline fun <reified T : Any> SaveState.save(value: T) {
    save(typeOf<T>().toString(), value)
}

interface LoadState {
    fun <T> loadOrNull(qualifier: String, deserializer: DeserializationStrategy<T>): T?
}

inline fun <reified T : Any> LoadState.loadOrNull(qualifier: String): T? =
    loadOrNull(qualifier, serializer<T>())

inline fun <reified T : Any> LoadState.loadOrNull(): T? =
    loadOrNull(typeOf<T>().toString())

fun <T : Any> LoadState.load(qualifier: String, deserializer: DeserializationStrategy<T>): T =
    loadOrNull(qualifier, deserializer) ?: throw NullStateException(qualifier)

inline fun <reified T : Any> LoadState.load(qualifier: String): T {
    return load(qualifier, serializer())
}

inline fun <reified T : Any> LoadState.load(): T {
    return load(typeOf<T>().toString())
}

interface State : SaveState, LoadState

internal class NBTComponentState(private val root: CompoundTag = buildCompoundTag { }) : State {

    private companion object {
        private val format = NBTSerialFormat() + NBTBinaryCodec(NBTBinaryConfig.KBT)
        private val json = Json(JsonConfiguration.Stable)
    }

    constructor(bytes: ByteArray) : this(format.read(ByteArrayInput(bytes)))


    override fun <T> save(qualifier: String, serializer: SerializationStrategy<T>, value: T) {
        with(root) {
            qualifier(format.toNBT(serializer, value))
        }
    }

    override fun <T> loadOrNull(qualifier: String, deserializer: DeserializationStrategy<T>): T? =
        root.value[qualifier]?.let { format.fromNBT(deserializer, it) }

    override fun toByteArray(): ByteArray {
        return format.write(root)
    }
}

class NullStateException(qualifier: String) : NullPointerException("no property \"$qualifier\" in saved state")

fun State(): State = NBTComponentState()

fun SaveState(): SaveState = State()

fun LoadState(bytes: ByteArray): LoadState = NBTComponentState(bytes)
