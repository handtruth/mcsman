package com.handtruth.mc.mcsman.compiler

import com.google.auto.service.AutoService
import com.handtruth.mc.mcsman.common.event.MCSManEvent
import com.handtruth.mc.mcsman.event.Event
import com.hendraanggrian.kotlinpoet.buildFileSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION_NAME)
@AutoService(Processor::class)
class MCSManEventProcessor : MCSManAbstractProcessor() {

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(MCSManEvent::class.java.canonicalName)

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val eventClasses = roundEnv.getElementsAnnotatedWith(MCSManEvent::class.java)
        val eventType = mirrorOf<Event>()
        for (clazz in eventClasses) {
            val type = clazz.asType()
            if (!types.isAssignable(type, eventType)) {
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "MCSMan event class $type does not inherit $eventType")
                return false
            }
            clazz as TypeElement
            @Suppress("DEPRECATION")
            val className = clazz.asClassName()
            val specs = clazz.toTypeSpec()
            when (specs.kind) {
                TypeSpec.Kind.INTERFACE -> processInterface(className, specs)
                TypeSpec.Kind.CLASS -> processClass(className, specs)
                else -> bad("Only interfaces or classes may be MCSMan event")
            }
        }
        return true
    }

    private fun processClass(className: ClassName, specs: TypeSpec) {
        if (!specs.modifiers.contains(KModifier.DATA))
            bad("Class $className is not a data class")
        val packageName = className.packageName
        val serializerName = "${className.simpleName}Serializer"
        buildFileSpec(packageName, serializerName) {
            types {
                addObject(serializerName) {
                    annotations {
                        add<Serializer> {
                            addMember("%T::class", className)
                        }
                    }
                }
            }
        }.writeTo(generatedDir)
    }

    private fun processInterface(className: ClassName, specs: TypeSpec) {
        val projectionName = "${className.simpleName}Projection"
        buildFileSpec(className.packageName, projectionName) {
            types {
                addClass(projectionName) {
                    annotations {
                        add<Serializable>()
                    }
                    addModifiers(KModifier.DATA)
                    superinterfaces[className] = null
                    val properties = collectAllProperties(specs).values
                    primaryConstructor {
                        parameters {
                            for (property in properties) {
                                add(property.name, property.type)
                            }
                        }
                    }
                    properties {
                        for (property in properties) {
                            add(property.name, property.type, KModifier.OVERRIDE) {
                                initializer(property.name)
                            }
                        }
                    }
                }
            }
        }.writeTo(generatedDir)
    }

}
