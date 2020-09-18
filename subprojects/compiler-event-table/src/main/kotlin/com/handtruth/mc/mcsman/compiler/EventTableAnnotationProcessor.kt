package com.handtruth.mc.mcsman.compiler

import com.google.auto.service.AutoService
import com.handtruth.mc.mcsman.common.event.MCSManEvent
import com.handtruth.mc.mcsman.server.event.InterfaceEventTable
import com.hendraanggrian.kotlinpoet.asNotNull
import com.hendraanggrian.kotlinpoet.asNullable
import com.hendraanggrian.kotlinpoet.buildFileSpec
import com.hendraanggrian.kotlinpoet.parameterizedBy
import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.type.MirroredTypesException
import javax.lang.model.type.TypeMirror

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION_NAME)
@AutoService(Processor::class)
class EventTableAnnotationProcessor : MCSManAbstractProcessor() {

    override fun getSupportedAnnotationTypes() = setOf(MCSManEvent.Table::class.java.canonicalName)

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv ?: return true
        roundEnv.getElementsAnnotatedWith(MCSManEvent.Table::class.java)
            .asSequence()
            .map { it.getAnnotationsByType(MCSManEvent.Table::class.java) }
            .flatMap { it.asSequence() }
            .map { extractTypes(it) }
            .flatten()
            .map { elementOf(it) as TypeElement }
            .forEach { buildTable(it) }
        return true
    }

    private fun extractTypes(annotation: MCSManEvent.Table): List<TypeMirror> {
        return try {
            annotation.events.map { mirrorOf(it) }
        } catch (e: MirroredTypesException) {
            e.typeMirrors
        }
    }

    private fun n(type: TypeName, nullable: Boolean) = if (nullable) type.asNullable() else type.asNotNull()

    private fun kType2Column(name: String, type: TypeName): Pair<TypeName, CodeBlock> {
        val n = type.isNullable
        val nullable = if (n) ".nullable()" else ""
        return when (val notNull = type.asNotNull()) {
            STRING -> type to CodeBlock.of("varchar(%S, com.handtruth.mc.mcsman.server.model.varCharLength)$nullable", name)
            BOOLEAN -> type to CodeBlock.of("bool(%S)$nullable", name)
            BYTE, SHORT -> n(SHORT, n) to CodeBlock.of("short(%S)$nullable", name)
            INT -> type to CodeBlock.of("integer(%S)$nullable", name)
            LONG -> type to CodeBlock.of("long(%S)$nullable", name)
            FLOAT -> type to CodeBlock.of("float(%S)$nullable", name)
            DOUBLE -> type to CodeBlock.of("double(%S)$nullable", name)
            BYTE_ARRAY -> n(ExposedBlob::class.asTypeName(), n) to CodeBlock.of("blob(%S)$nullable", name)
            else -> {
                if (elementOf(notNull.toString()).toTypeSpec().isEnum)
                    n(STRING, n) to CodeBlock.of(
                        "varchar(%S, com.handtruth.mc.mcsman.server.model.varCharLength)$nullable",
                        name
                    )
                else
                    n(STRING, n) to CodeBlock.of("text(%S)$nullable", name)
            }
        }
    }

    private fun buildTable(typeElement: TypeElement) {
        val packageName = packageOf(typeElement).toString()
        val typeSpec = typeElement.toTypeSpec()
        val tableName = "${typeElement.simpleName}Table"
        val columnType = Column::class.asTypeName()
        val annotation = typeElement.getAnnotation(MCSManEvent::class.java)
        val properties = run {
            val props = typeSpec.superinterfaces.keys.asSequence().map {
                collectAllProperties((it as ClassName).toTypeSpec()!!).filterValues { p ->
                    val className = when (val type = p.type) {
                        is ClassName -> type
                        is ParameterizedTypeName -> type.rawType
                        else -> throw UnsupportedOperationException()
                    }
                    if (className.asNotNull() == ANY)
                        return@filterValues false
                    if (className.packageName.startsWith("kotlin"))
                        return@filterValues true
                    val spec = className.toTypeSpec()
                    if (spec == null) {
                        true
                    } else {
                        KModifier.OPEN !in spec.modifiers &&
                                KModifier.ABSTRACT !in spec.modifiers &&
                                spec.kind != TypeSpec.Kind.INTERFACE
                    }
                }.keys
            }.reduce<Collection<String>, Collection<String>> { a, b -> a + b }
            val map = mutableMapOf<String, PropertySpec>()
            typeSpec.propertySpecs.associateByTo(map) { it.name }.keys.removeAll(props)
            map.values
        }
        buildFileSpec(packageName, tableName) {
            types {
                addObject(tableName) {
                    superclass<InterfaceEventTable>()
                    addSuperclassConstructorParameter("%S", annotation.name)
                    properties {
                        for (property in properties) {
                            val (type, code) = kType2Column(property.name, property.type)
                            add(property.name, columnType.parameterizedBy(type)) {
                                initializer = code
                            }
                        }
                    }
                }
            }
        }.writeTo(generatedDir)
    }
}
