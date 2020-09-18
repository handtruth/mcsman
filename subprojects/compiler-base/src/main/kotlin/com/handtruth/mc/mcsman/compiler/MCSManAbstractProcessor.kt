@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "RemoveRedundantQualifierName", "NOTHING_TO_INLINE")

package com.handtruth.mc.mcsman.compiler

import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.classinspector.elements.ElementsClassInspector
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import com.squareup.kotlinpoet.metadata.specs.toTypeSpec
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import kotlin.reflect.KClass

@OptIn(KotlinPoetMetadataPreview::class)
abstract class MCSManAbstractProcessor : AbstractProcessor() {

    val log: Log = object : Log(LogLevel.Debug) {
        override fun write(lvl: LogLevel, message: Any?) {
            val str = message.toString() + System.lineSeparator()
            when (lvl) {
                LogLevel.None -> {}
                LogLevel.Fatal, LogLevel.Error -> processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, str)
                LogLevel.Warning -> processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, str)
                LogLevel.Info -> processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, str)
                LogLevel.Verbose -> processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, str)
                LogLevel.Debug -> processingEnv.messager.printMessage(Diagnostic.Kind.OTHER, str)
            }
        }
    }

    protected val elements by lazy { processingEnv.elementUtils }
    protected val types by lazy { processingEnv.typeUtils }

    protected val inspector by lazy { ElementsClassInspector.create(elements, types) }

    private companion object {
        const val errorNotFoundGeneratedDir = "Can't find the target directory for generated Kotlin files."
    }

    val generatedDir by lazy {
        File(
            processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME] ?: run {
                log.fatal { errorNotFoundGeneratedDir }
            }
        )
    }

    protected fun elementOf(name: String) = elements.getTypeElement(name)
    protected fun elementOf(name: ClassName) = elementOf(name.canonicalName)
    protected fun elementOf(type: KClass<*>) = elementOf(type.java.canonicalName)
    protected inline fun <reified T> elementOf() = elementOf(T::class)
    protected fun elementOf(mirror: TypeMirror) = types.asElement(mirror)

    protected fun mirrorOf(type: KClass<*>) = elementOf(type).asType()
    protected inline fun <reified T> mirrorOf() = elementOf<T>().asType()

    protected fun packageOf(element: Element) = elements.getPackageOf(element)

    protected fun bad(msg: String): Nothing {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
        throw RuntimeException(msg)
    }

    protected fun TypeElement.toTypeSpec(): TypeSpec = toTypeSpec(inspector)

    protected inline fun <reified T : Any> typeSpecOf(): TypeSpec = T::class.toTypeSpec(inspector)

    protected fun ClassName.toTypeSpec(): TypeSpec? {
        return when (this) {
            ANY -> typeSpecOf<Any>()
            ARRAY -> typeSpecOf<Array<*>>()
            UNIT -> typeSpecOf<Unit>()
            BOOLEAN -> typeSpecOf<Boolean>()
            BYTE -> typeSpecOf<Byte>()
            SHORT -> typeSpecOf<Short>()
            INT -> typeSpecOf<Int>()
            LONG -> typeSpecOf<Long>()
            CHAR -> typeSpecOf<Char>()
            FLOAT -> typeSpecOf<Float>()
            DOUBLE -> typeSpecOf<Double>()
            STRING -> typeSpecOf<String>()
            BOOLEAN_ARRAY -> typeSpecOf<BooleanArray>()
            BYTE_ARRAY -> typeSpecOf<ByteArray>()
            CHAR_ARRAY -> typeSpecOf<CharArray>()
            SHORT_ARRAY -> typeSpecOf<ShortArray>()
            INT_ARRAY -> typeSpecOf<IntArray>()
            LONG_ARRAY -> typeSpecOf<LongArray>()
            FLOAT_ARRAY -> typeSpecOf<FloatArray>()
            DOUBLE_ARRAY -> typeSpecOf<DoubleArray>()
            else -> elementOf(this)?.toTypeSpec()
        }
    }

    private fun collectAllProperties(map: MutableMap<String, PropertySpec>, specs: TypeSpec) {
        for (property in specs.propertySpecs) {
            map.putIfAbsent(property.name, property)
        }
        for (interfaze in specs.superinterfaces.keys) {
            interfaze as ClassName
            collectAllProperties(map, interfaze.toTypeSpec()!!)
        }
    }

    protected fun collectAllProperties(specs: TypeSpec): Map<String, PropertySpec> {
        val map = hashMapOf<String, PropertySpec>()
        collectAllProperties(map, specs)
        return map
    }
}
