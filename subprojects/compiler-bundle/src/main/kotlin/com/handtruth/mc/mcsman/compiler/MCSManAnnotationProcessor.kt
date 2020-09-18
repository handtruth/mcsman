package com.handtruth.mc.mcsman.compiler

import com.google.auto.service.AutoService
import com.handtruth.kommon.info
import com.handtruth.mc.mcsman.common.event.MCSManEvent
import com.handtruth.mc.mcsman.server.bundle.manifestNamespace
import com.handtruth.mc.mcsman.server.module.MCSManModule
import com.handtruth.mc.mcsman.server.service.MCSManService
import org.redundent.kotlin.xml.xml
import java.io.File
import java.io.Writer
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.type.MirroredTypesException
import javax.tools.StandardLocation

const val mcsmanManifest = "com.handtruth.mc.mcsman.manifest"
const val mcsmanVersion = "com.handtruth.mc.mcsman.version"
const val mcsmanArtifact = "com.handtruth.mc.mcsman.artifact"
const val mcsmanGroup = "com.handtruth.mc.mcsman.group"

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(KAPT_KOTLIN_GENERATED_OPTION_NAME, mcsmanVersion, mcsmanArtifact, mcsmanGroup, mcsmanManifest)
@AutoService(Processor::class)
class MCSManAnnotationProcessor : MCSManAbstractProcessor() {
    override fun getSupportedAnnotationTypes() = setOf(
        MCSManModule::class.java.canonicalName,
        MCSManService::class.java.canonicalName,
        MCSManModule.Artifact::class.java.canonicalName,
        MCSManEvent.Register::class.java.canonicalName
    )

    private inline fun <reified A : Annotation> RoundEnvironment.select(): List<ManifestMember<A>> {
        return getElementsAnnotatedWith(A::class.java)
            ?.map {
                it as TypeElement
                val annotation = it.getAnnotation(A::class.java)
                val className = it.simpleName.toString()
                val pack = processingEnv.elementUtils.getPackageOf(it).toString()
                ManifestMember(className, pack, annotation, it)
            } ?: emptyList()
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        if (annotations.isNullOrEmpty() || roundEnv == null)
            return true
        val modules = roundEnv.select<MCSManModule>()
        val services = roundEnv.select<MCSManService>()
        val events: List<String> = roundEnv.getElementsAnnotatedWith(MCSManEvent.Register::class.java)
            ?.flatMap { element ->
                element.getAnnotationsByType(MCSManEvent.Register::class.java)!!.map { annotation ->
                    try {
                        annotation.events.map { it.java.name }
                    } catch (e: MirroredTypesException) {
                        e.typeMirrors.map { it.toString() }
                    }
                }
            }?.flatten() ?: emptyList()
        log.info(modules) { "modules" }
        log.info(services) { "services" }
        if (modules.isEmpty() && services.isEmpty())
            return true
        generateManifest(modules, services, events)
        return true
    }

    data class ManifestMember<A : Annotation>(
        val className: String,
        val pack: String,
        val annotation: A,
        val element: TypeElement
    )

    private fun generateManifest(
        modules: List<ManifestMember<MCSManModule>>,
        services: List<ManifestMember<MCSManService>>,
        events: List<String>
    ) {
        val options = processingEnv.options
        val document = xml("manifest") {
            xmlns = manifestNamespace

            attribute("group", options[mcsmanGroup]!!)
            attribute("artifact", options[mcsmanArtifact]!!)
            attribute("version", options[mcsmanVersion]!!)

            for ((className, pack, annotation, element) in modules) "module" {
                attribute("class", "$pack.$className")

                if (annotation.before.isNotEmpty()) "before" {
                    for (item in annotation.before) "item" {
                        -item
                    }
                }
                if (annotation.after.isNotEmpty()) "after" {
                    for (item in annotation.after) "item" {
                        -item
                    }
                }

                for (artifact in element.getAnnotationsByType(MCSManModule.Artifact::class.java)) "artifact" {
                    attribute("class", artifact.className)
                    attribute("type", artifact.type)
                    attribute("platform", artifact.platform)
                    attribute("uri", artifact.uri)
                }
            }

            for ((className, pack) in services) "service" {
                attribute("class", "$pack.$className")
            }

            for (event in events) "event" {
                attribute("class", event)
            }
        }
        val file: Writer = options[mcsmanManifest]?.let { File(it).writer() } ?: processingEnv.filer.createResource(
            StandardLocation.CLASS_OUTPUT, "", "META-INF/mcsman.xml"
        ).openWriter()
        file.use {
            it.write(document.toString())
        }
    }
}
