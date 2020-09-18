package com.handtruth.mc.mcsman.server.model.internal

import kotlinx.serialization.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import java.time.Instant

class DBEventDecoder(private val stringFormat: StringFormat) : CompositeDecoder, Decoder {
    override val context get() = stringFormat.context
    override val updateMode get() = UpdateMode.OVERWRITE

    private lateinit var columns: Map<String, Column<*>>
    private lateinit var result: ResultRow

    fun switch(columns: Map<String, Column<*>>, resultRow: ResultRow) {
        this.columns = columns
        result = resultRow
        index = 0
    }

    private fun getColumn(descriptor: SerialDescriptor, index: Int): Column<*> =
        columns[descriptor.getElementName(index)]
            ?: error("No such column ${descriptor.getElementName(index)} (maybe bug)")

    private inline fun <reified T> retrieveValue(descriptor: SerialDescriptor, index: Int): T {
        val column = getColumn(descriptor, index)
        return result[column] as T
    }

    private var index = 0

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean =
        retrieveValue(descriptor, index)

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte =
        retrieveValue<Short>(descriptor, index).toByte()

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char =
        retrieveValue(descriptor, index)

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        retrieveValue(descriptor, index)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = index++

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float  =
        retrieveValue(descriptor, index)

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
        retrieveValue(descriptor, index)

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        val column = getColumn(descriptor, index)
        val data = result[column]
        return if (column.name == "timestamp")
            (data as Instant).toEpochMilli()
        else
            data as Long
    }

    override fun <T : Any> decodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>): T? {
        val column = getColumn(descriptor, index)
        return if (result.getOrNull(column) != null)
            decodeSerializableElement(descriptor, index, deserializer)
        else
            null
    }

    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    override fun <T> decodeSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>): T {
        return when (deserializer.descriptor.kind) {
            PrimitiveKind.BOOLEAN -> decodeBooleanElement(descriptor, index)
            PrimitiveKind.BYTE -> decodeByteElement(descriptor, index)
            PrimitiveKind.SHORT -> decodeShortElement(descriptor, index)
            PrimitiveKind.CHAR -> decodeCharElement(descriptor, index)
            PrimitiveKind.INT -> decodeIntElement(descriptor, index)
            PrimitiveKind.LONG -> decodeLongElement(descriptor, index)
            PrimitiveKind.FLOAT -> decodeFloatElement(descriptor, index)
            PrimitiveKind.DOUBLE -> decodeDoubleElement(descriptor, index)
            PrimitiveKind.STRING -> decodeStringElement(descriptor, index)
            UnionKind.ENUM_KIND -> deserializer.descriptor.getElementIndex(decodeStringElement(descriptor, index))
            else -> {
                val column = getColumn(descriptor, index)
                val data = result[column] as String
                stringFormat.parse(deserializer, data)
            }
        } as T
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short =
        retrieveValue(descriptor, index)

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String =
        retrieveValue(descriptor, index)

    override fun decodeUnitElement(descriptor: SerialDescriptor, index: Int) = Unit

    override fun endStructure(descriptor: SerialDescriptor) = Unit

    override fun <T : Any> updateNullableSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>, old: T?): Nothing =
        throw UnsupportedOperationException()

    override fun <T> updateSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>, old: T): Nothing =
        throw UnsupportedOperationException()

    override fun decodeSequentially() = true

    private inline val error: Nothing get() = throw UnsupportedOperationException("decoder does not support primitives")

    override fun beginStructure(descriptor: SerialDescriptor, vararg typeParams: KSerializer<*>) = this
    override fun decodeBoolean() = error
    override fun decodeByte() = error
    override fun decodeChar() = error
    override fun decodeDouble() = error
    override fun decodeEnum(enumDescriptor: SerialDescriptor) = error
    override fun decodeFloat() = error
    override fun decodeInt() = error
    override fun decodeLong() = error
    override fun decodeNotNullMark() = error
    override fun decodeNull() = error
    override fun decodeShort() = error
    override fun decodeString() = error
    override fun decodeUnit() = error
}
