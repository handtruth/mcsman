@file:Suppress("UNUSED_VARIABLE")

package com.handtruth.mc.mcsman.gradle

import com.handtruth.mc.mcsman.server.bundle.Bundles
import com.handtruth.mc.mcsman.server.bundle.manifestNamespace
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin
import org.redundent.kotlin.xml.xml
import java.io.File
import java.net.URI
import java.util.concurrent.Callable

class MCSPackBundlePlugin : MCSPackPlugin() {

    override fun Project.applyMCSPack() {
        apply<SerializationGradleSubplugin>()
        val kapt = prepareKapt()

        val mcsmanDir = File("$buildDir/mcspack")
        val manifestKapt = File(mcsmanDir, "manifest-kapt.xml")
        val manifestGradle = File(mcsmanDir, "manifest-gradle.xml")
        val manifestCustom = File(projectDir, "src/mcsman.xml")
        val manifestMerged = File(mcsmanDir, "mcsman.xml")

        val mcspackExt = extensions.create<MCSPackBundleExtension>("mcspack")

        configure<KaptExtension> {
            arguments {
                arg("com.handtruth.mc.mcsman.group", project.group)
                arg("com.handtruth.mc.mcsman.artifact", project.name)
                arg("com.handtruth.mc.mcsman.version", project.version)
                arg("com.handtruth.mc.mcsman.manifest", manifestKapt.absolutePath)
                arg("com.handtruth.mc.mcsman.mcsmanVersion", this@MCSPackBundlePlugin.version)
            }
        }

        val optionalMcsmanBundle by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
        }
        val mcsmanBundle by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
        }
        val providedCompile by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
            extendsFrom(optionalMcsmanBundle)
            extendsFrom(mcsmanBundle)
        }
        val providedRuntime by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
            extendsFrom(providedCompile)
        }
        val runtime by configurations.getting {
            extendsFrom(providedRuntime)
        }

        val sourceSets = project.the<SourceSetContainer>()

        sourceSets["main"].compileClasspath += providedCompile

        val implementation by configurations.getting

        dependencies {
            val mcsmanPlatform = platform(mcsman("bom:${this@MCSPackBundlePlugin.version}"))
            val handtruthPlatform = platform("com.handtruth.internal:platform:$handtruthPlatformVersion")
            kapt(mcsmanPlatform); kapt(handtruthPlatform)
            runtime(mcsmanPlatform); runtime(handtruthPlatform)
            implementation(mcsmanPlatform); implementation(handtruthPlatform)
            providedCompile(mcsmanPlatform); providedCompile(handtruthPlatform)
            providedCompile(mcsman("core"))
            kapt(mcsman("compiler-bundle"))
        }

        val mcsmanBundleManifest by tasks.creating {
            group = "mcspack"
            doLast {
                mcsmanDir.mkdirs()
                val document = xml("manifest") {
                    xmlns = manifestNamespace

                    attribute("group", project.group)
                    attribute("artifact", project.name)
                    attribute("version", project.version)
                    attribute("mcsmanVersion", this@MCSPackBundlePlugin.version)

                    val hasDeps = mcsmanBundle.allDependencies.isNotEmpty() ||
                            optionalMcsmanBundle.allDependencies.isNotEmpty()
                    if (hasDeps) "dependencies" {
                        for (dependency in mcsmanBundle.allDependencies) "dependency" {
                            attribute("group", dependency.group.orEmpty())
                            attribute("artifact", dependency.name)
                            dependency.version?.let {
                                attribute("version", it)
                            }
                        }
                        for (dependency in optionalMcsmanBundle.allDependencies) "dependency" {
                            attribute("group", dependency.group.orEmpty())
                            attribute("artifact", dependency.name)
                            dependency.version?.let {
                                attribute("version", it)
                            }
                            attribute("require", false)
                        }
                    }
                    for ((module, artifacts) in mcspackExt.artifacts.groupBy { it.module }) "module" {
                        if (module != null)
                            attribute("class", module)
                        for (artifact in artifacts) {
                            val link = artifact.source as? Project ?: continue
                            "artifact" {
                                attribute("class", artifact.type)
                                attribute("type", "maven")
                                attribute("platform", "jvm")
                                attribute("uri", URI("mvn:${link.group}/${link.name}/${link.version}"))
                            }
                        }
                    }
                }
                manifestGradle.writer().use { document.writeTo(it) }
            }
        }

        val kapts = tasks.withType<KaptTask> {
            dependsOn(mcsmanBundleManifest)
        }

        val mcsmanMergeManifests by tasks.creating {
            group = "mcspack"
            dependsOn(kapts)
            doLast {
                val resultA = if (manifestKapt.exists())
                    Bundles.mergeManifests(manifestKapt.readText(), manifestGradle.readText())
                else
                    manifestGradle.readText()
                val resultB = if (manifestCustom.exists())
                    Bundles.mergeManifests(resultA, manifestCustom.readText())
                else
                    resultA
                manifestMerged.writeText(resultB)
            }
        }

        @Suppress("UNUSED_VALUE")
        val mcsmanBundleJar by tasks.creating(Jar::class) {
            description = "Build MCSMan resource bundle"
            group = "mcspack"

            var baseName by archiveBaseName
            baseName = "$baseName-bundle"

            val classpath by lazy { sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]!!.runtimeClasspath }

            dependsOn(Callable { classpath }, mcsmanMergeManifests)
            this.metaInf {
                from(Callable { manifestMerged })
            }
            from(Callable<Iterable<Any>> {
                val packedClasspath = classpath - providedRuntime
                packedClasspath.map { if (it.exists() && !it.isDirectory) zipTree(it) else it }
            })
        }
    }
}
