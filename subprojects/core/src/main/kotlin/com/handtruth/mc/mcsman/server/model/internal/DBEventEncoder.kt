package com.handtruth.mc.mcsman.server.model.internal

import com.handtruth.mc.mcsman.server.event.EventTableBase
import kotlinx.serialization.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.InsertStatement

internal class DBEventEncoder(private val stringFormat: StringFormat) : CompositeEncoder, Encoder {

    private val primitiveEncoder = DBPrimitiveEncoder(stringFormat.context)

    override val context get() = stringFormat.context

    private lateinit var table: Table
    private lateinit var insert: InsertStatement<*>

    fun switch(table: EventTableBase, insertStatement: InsertStatement<*>) {
        this.table = table
        insert = insertStatement
    }

    private fun <T> insertValue(descriptor: SerialDescriptor, index: Int, value: T) {
        val name = descriptor.getElementName(index)
        val column = table.columns.find { it.name == name } ?: return
        @Suppress("UNCHECKED_CAST")
        insert[column as Column<T>] = value
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) =
        insertValue(descriptor, index, value)

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) =
        insertValue(descriptor, index, value.toShort())

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) =
        insertValue(descriptor, index, value)

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) =
        insertValue(descriptor, index, value)

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) =
        insertValue(descriptor, index, value)

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) =
        insertValue(descriptor, index, value)

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) =
        insertValue(descriptor, index, value)

    override fun <T : Any> encodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?) {
        if (value == null)
            insertValue<T?>(descriptor, index, value)
        else
            encodeSerializableElement(descriptor, index, serializer, value)
    }

    override fun <T> encodeSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
        when (value) {
            is Boolean -> encodeBooleanElement(descriptor, index, value)
            is Byte -> encodeByteElement(descriptor, index, value)
            is Short -> encodeShortElement(descriptor, index, value)
            is Char -> encodeCharElement(descriptor, index, value)
            is Int -> encodeIntElement(descriptor, index, value)
            is Long -> encodeLongElement(descriptor, index, value)
            is Float -> encodeFloatElement(descriptor, index, value)
            is Double -> encodeDoubleElement(descriptor, index, value)
            is String -> encodeStringElement(descriptor, index, value)
            Unit -> encodeUnitElement(descriptor, index)
            else -> {
                when (serializer.descriptor.kind) {
                    is PrimitiveKind, UnionKind.ENUM_KIND -> {
                        serializer.serialize(primitiveEncoder, value)
                        insertValue(descriptor, index, primitiveEncoder.value)
                    }
                    else -> {
                        val data = stringFormat.stringify(serializer, value)
                        insertValue(descriptor, index, data)
                    }
                }
            }
        }
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) =
        insertValue(descriptor, index, value)

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) =
        insertValue(descriptor, index, value)

    override fun encodeUnitElement(descriptor: SerialDescriptor, index: Int) = Unit

    override fun endStructure(descriptor: SerialDescriptor) = Unit

    override fun beginStructure(descriptor: SerialDescriptor, vararg typeSerializers: KSerializer<*>) = this

    private inline val error: Nothing get() = throw UnsupportedOperationException("Encoder does not support primitives")

    override fun encodeBoolean(value: Boolean) = error
    override fun encodeByte(value: Byte) = error
    override fun encodeChar(value: Char) = error
    override fun encodeDouble(value: Double) = error
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = error
    override fun encodeFloat(value: Float) = error
    override fun encodeInt(value: Int) = error
    override fun encodeLong(value: Long) = error
    override fun encodeNull() = error
    override fun encodeShort(value: Short) = error
    override fun encodeString(value: String) = error
    override fun encodeUnit() = error
}
