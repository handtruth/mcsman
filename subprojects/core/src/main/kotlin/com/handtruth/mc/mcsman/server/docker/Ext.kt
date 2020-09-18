@file:Suppress("EqualsOrHashCode")

package com.handtruth.mc.mcsman.server.docker

import com.handtruth.mc.paket.util.Path
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.*
import kotlinx.serialization.builtins.list
import kotlinx.serialization.builtins.serializer

@Serializable(Ext.Serializer::class)
sealed class Ext {
    operator fun plus(other: Ext) = persistentListOf(this, other)

    interface SerializerPart<E : Ext> : KSerializer<E> {
        fun deserializePart(decoder: CompositeDecoder, type: String): E

        override fun deserialize(decoder: Decoder): E {
            return decoder.decodeStructure(descriptor) {
                val type = decodeStringElement(descriptor, 0)
                deserializePart(this, type)
            }
        }
    }

    object Serializer : KSerializer<Ext>, SerializerPart<Ext> {
        override val descriptor = SerialDescriptor("docker") {
            element("ext", String.serializer().descriptor)
        }

        override fun deserializePart(decoder: CompositeDecoder, type: String): Ext {
            return when {
                type.startsWith("port") -> Port.Serializer.deserializePart(decoder, type)
                type.startsWith("volume") -> Volume.Serializer.deserializePart(decoder, type)
                type.startsWith("env") -> Env.Serializer.deserializePart(decoder, type)
                type.startsWith("cmd") -> Cmd.Serializer.deserializePart(decoder, type)
                else -> throw UnsupportedOperationException()
            }
        }

        override fun serialize(encoder: Encoder, value: Ext) {
            when (value) {
                is Port -> Port.Serializer.serialize(encoder, value)
                is Volume -> Volume.Serializer.serialize(encoder, value)
                is Env -> Env.Serializer.serialize(encoder, value)
                is Cmd -> Cmd.Serializer.serialize(encoder, value)
            }
        }
    }

    @Serializable(Port.Serializer::class)
    sealed class Port(val internal: UShort, val type: Protocols) : Ext() {

        protected fun equals(other: Port) = internal == other.internal && type == other.type
        override fun equals(other: Any?) = other is Port && equals(other)
        override fun hashCode() = internal.hashCode() + type.hashCode().rotateLeft(16)

        @Serializable
        enum class Protocols {
            TCP, UDP, SCTP;

            override fun toString() = name.toLowerCase()
        }

        object Serializer : KSerializer<Port>, SerializerPart<Port> {
            override val descriptor = SerialDescriptor("docker.port") {
                element("ext", String.serializer().descriptor)
                element("internal", Short.serializer().descriptor)
                element("type", Protocols.serializer().descriptor)
            }

            override fun deserializePart(decoder: CompositeDecoder, type: String): Port {
                return when (type) {
                    "port.internal" -> Internal.Serializer.deserializePart(decoder, type)
                    "port.external" -> Internal.Serializer.deserializePart(decoder, type)
                    else -> throw UnsupportedOperationException()
                }
            }

            override fun serialize(encoder: Encoder, value: Port) {
                when (value) {
                    is Internal -> Internal.Serializer.serialize(encoder, value)
                    is External -> External.Serializer.serialize(encoder, value)
                }
            }
        }

        @Serializable(Internal.Serializer::class)
        class Internal(port: UShort, type: Protocols = Protocols.TCP) : Port(port, type) {
            override fun equals(other: Any?) = other is Internal && equals(other)
            override fun toString() = "$internal/$type"

            object Serializer : KSerializer<Internal>, SerializerPart<Internal> {
                override val descriptor = SerialDescriptor("docker.port.internal") {
                    element("ext", String.serializer().descriptor)
                    element("internal", Short.serializer().descriptor)
                    element("type", Protocols.serializer().descriptor)
                }

                override fun deserializePart(decoder: CompositeDecoder, type: String): Internal {
                    val port = decoder.decodeShortElement(descriptor, 1)
                    val portType = decoder.decodeSerializableElement(descriptor, 2, Protocols.serializer())
                    return Internal(port.toUShort(), portType)
                }

                override fun serialize(encoder: Encoder, value: Internal) {
                    encoder.encodeStructure(descriptor) {
                        encodeStringElement(descriptor, 0, "port.internal")
                        encodeShortElement(descriptor, 1, value.internal.toShort())
                        encodeSerializableElement(descriptor, 2, Protocols.serializer(), value.type)
                    }
                }
            }
        }

        @Serializable(External.Serializer::class)
        class External(internal: UShort, val external: UShort = internal, type: Protocols = Protocols.TCP) :
            Port(internal, type) {

            override fun equals(other: Any?) = other is External && equals(other) && external == other.external
            override fun hashCode() = super.hashCode() xor external.rotateRight(8).toInt()
            override fun toString() = "$internal/$type->$external"

            object Serializer : KSerializer<External>, SerializerPart<External> {
                override val descriptor = SerialDescriptor("docker.port.external") {
                    element("ext", String.serializer().descriptor)
                    element("internal", Short.serializer().descriptor)
                    element("type", Protocols.serializer().descriptor)
                    element("external", Short.serializer().descriptor)
                }

                override fun deserializePart(decoder: CompositeDecoder, type: String): External {
                    val port = decoder.decodeShortElement(descriptor, 1)
                    val portType = decoder.decodeSerializableElement(descriptor, 2, Protocols.serializer())
                    val external = decoder.decodeShortElement(descriptor, 3)
                    return External(port.toUShort(), external.toUShort(), portType)
                }

                override fun serialize(encoder: Encoder, value: External) {
                    encoder.encodeStructure(descriptor) {
                        encodeStringElement(descriptor, 0, "port.internal")
                        encodeShortElement(descriptor, 1, value.internal.toShort())
                        encodeSerializableElement(descriptor, 2, Port.Protocols.serializer(), value.type)
                        encodeShortElement(descriptor, 3, value.external.toShort())
                    }
                }
            }
        }
    }

    @Serializable(Volume.Serializer::class)
    sealed class Volume(val path: Path, val accessType: AccessTypes) : Ext() {

        @Serializable
        enum class AccessTypes {
            RO, RW;

            override fun toString() = name.toLowerCase()
        }

        protected fun equals(other: Volume) = path == other.path && accessType == other.accessType
        override fun equals(other: Any?) = other is Volume && equals(other)
        override fun hashCode() = path.hashCode() + accessType.hashCode().rotateLeft(8)

        object Serializer : KSerializer<Volume>, SerializerPart<Volume> {
            override val descriptor = SerialDescriptor("docker.volume") {
                element("ext", String.serializer().descriptor)
                element("path", PrimitiveDescriptor("path", PrimitiveKind.STRING))
                element("accessType", AccessTypes.serializer().descriptor)
            }

            override fun deserializePart(decoder: CompositeDecoder, type: String): Volume {
                return when (type) {
                    "volume.server" -> Server.Serializer.deserializePart(decoder, type)
                    else -> throw UnsupportedOperationException()
                }
            }

            override fun serialize(encoder: Encoder, value: Volume) {
                when (value) {
                    is Server -> Server.Serializer.serialize(encoder, value)
                }
            }
        }

        @Serializable(Server.Serializer::class)
        class Server(
            val volume: Int,
            path: Path, accessType: AccessTypes = AccessTypes.RW
        ) : Volume(path, accessType) {

            constructor(
                volume: com.handtruth.mc.mcsman.server.server.Volume,
                path: Path, accessType: AccessTypes = AccessTypes.RW
            ) : this(volume.id, path, accessType)

            override fun equals(other: Any?) =
                other is Server && volume == other.volume && equals(other)

            override fun hashCode() = super.hashCode() + volume.rotateRight(8)
            override fun toString() = "#$volume:$path:$accessType"

            object Serializer : KSerializer<Server>, SerializerPart<Server> {
                override val descriptor = SerialDescriptor("docker.volume.server") {
                    element("ext", String.serializer().descriptor)
                    element("path", PrimitiveDescriptor("path", PrimitiveKind.STRING))
                    element("accessType", AccessTypes.serializer().descriptor)
                    element("volume", Int.serializer().descriptor)
                }

                override fun deserializePart(decoder: CompositeDecoder, type: String): Server {
                    val path = Path(decoder.decodeStringElement(descriptor, 1))
                    val accessType = decoder.decodeSerializableElement(descriptor, 2, AccessTypes.serializer())
                    val volume = decoder.decodeIntElement(descriptor, 3)
                    return Server(volume, path, accessType)
                }

                override fun serialize(encoder: Encoder, value: Server) {
                    encoder.encodeStructure(descriptor) {
                        encodeStringElement(descriptor, 0, "volume.server")
                        encodeStringElement(descriptor, 1, value.path.toString())
                        encodeSerializableElement(descriptor, 2, AccessTypes.serializer(), value.accessType)
                        encodeIntElement(descriptor, 3, value.volume)
                    }
                }
            }
        }
    }

    @Serializable(Env.Serializer::class)
    data class Env(val key: String, val value: String) : Ext() {
        override fun toString() = "$key=$value"

        object Serializer : KSerializer<Env>, SerializerPart<Env> {
            override val descriptor = SerialDescriptor("docker.env") {
                val stringDescriptor = String.serializer().descriptor
                element("ext", stringDescriptor)
                element("key", stringDescriptor)
                element("value", stringDescriptor)
            }

            override fun deserializePart(decoder: CompositeDecoder, type: String): Env {
                val key = decoder.decodeStringElement(descriptor, 1)
                val value = decoder.decodeStringElement(descriptor, 2)
                return Env(key, value)
            }

            override fun serialize(encoder: Encoder, value: Env) {
                encoder.encodeStructure(descriptor) {
                    encodeStringElement(descriptor, 0, "env")
                    encodeStringElement(descriptor, 1, value.key)
                    encodeStringElement(descriptor, 2, value.value)
                }
            }
        }
    }

    @Serializable(Cmd.Serializer::class)
    data class Cmd(val command: List<String>) : Ext() {

        constructor(vararg args: String) : this(listOf(*args))

        object Serializer : KSerializer<Cmd>, SerializerPart<Cmd> {
            override val descriptor = SerialDescriptor("docker.cmd") {
                val stringSerializer = String.serializer()
                val stringDescriptor = stringSerializer.descriptor
                element("ext", stringDescriptor)
                element("command", stringSerializer.list.descriptor)
            }

            override fun deserializePart(decoder: CompositeDecoder, type: String): Cmd {
                val command = decoder.decodeSerializableElement(descriptor, 1, String.serializer().list)
                return Cmd(command)
            }

            override fun serialize(encoder: Encoder, value: Cmd) {
                encoder.encodeStructure(Env.Serializer.descriptor) {
                    encodeStringElement(Env.Serializer.descriptor, 0, "cmd")
                    encodeSerializableElement(
                        Env.Serializer.descriptor, 1, String.serializer().list, value.command
                    )
                }
            }
        }
    }
}
