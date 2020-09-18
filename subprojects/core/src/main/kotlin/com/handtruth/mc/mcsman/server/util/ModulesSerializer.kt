package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.mcsman.server.module.ModuleConfig
import com.handtruth.mc.mcsman.server.module.Modules
import com.handtruth.mc.mcsman.server.yaml
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import org.koin.core.KoinComponent
import org.koin.core.inject

internal object ModulesSerializer : KSerializer<Map<String, ModuleConfig>>, KoinComponent {
    private val modules: Modules by inject()

    private const val name = "com.handtruth.mc.mcsman.util.ModulesSerializer"

    override val descriptor = SerialDescriptor(name, StructureKind.MAP) {
        element("key", String.serializer().descriptor)
        element("value", SerialDescriptor("$name.value", UnionKind.CONTEXTUAL))
    }
    
    private fun decodeConfig(composite: CompositeDecoder, name: String, index: Int): ModuleConfig? {
        val deserializer = modules.getOrNull(name)?.configDeserializer ?: return null
        return composite.decodeSerializableElement(descriptor, index, deserializer)
    }

    override fun deserialize(decoder: Decoder): Map<String, ModuleConfig> {
        return decoder.decodeStructure(descriptor) {
            val result: MutableMap<String, ModuleConfig> = hashMapOf()
            if (decodeSequentially()) {
                val size = decodeCollectionSize(descriptor)
                for (i in 0 until size step 2) {
                    val key = decodeStringElement(descriptor, i)
                    decodeConfig(this, key, i + 1)
                        ?.let { result[key] = it }
                }
            } else {
                val keys: MutableMap<Int, String> = hashMapOf()
                val values: MutableMap<Int, ModuleConfig> = hashMapOf()
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == -1)
                        break
                    val i = index shr 1
                    if (index and 1 == 0) {
                        keys[i] = decodeStringElement(descriptor, index)
                    } else {
                        val value = decodeConfig(
                            this,
                            keys[i]!!,
                            index
                        )
                        if (value == null)
                            keys.remove(i)
                        else
                            values[i] = value
                    }
                }
                (keys.values.asSequence() zip values.values.asSequence()).associateTo(result) { it }
            }
            for (module in modules.all) {
                if (module.name !in result)
                    result[module.name] = yaml.parse(module.configDeserializer, "{}")
            }
            result
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, ModuleConfig>) = throw UnsupportedOperationException()
}
