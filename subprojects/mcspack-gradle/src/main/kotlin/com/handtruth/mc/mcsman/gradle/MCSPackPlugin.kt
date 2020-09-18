package com.handtruth.mc.mcsman.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*
import kotlin.reflect.KProperty

abstract class MCSPackPlugin internal constructor(): Plugin<Project> {
    private val props = Properties()

    init {
        val resource = javaClass.classLoader.getResource("META-INF/mcsman-module-plugin.properties")
        props.load(resource!!.openStream()!!)
    }

    private operator fun Properties.getValue(thisRef: MCSPackBundlePlugin, property: KProperty<*>): String {
        return getProperty(property.name)!!
    }

    val group by props
    val name by props
    val version by props
    protected val handtruthPlatformVersion by props

    protected val platformType = Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java)

    protected fun mcsman(name: String) = "$group:mcsman-$name"

    protected open fun Project.applyMCSPack(): Unit = TODO("reserved for future")

    protected fun Project.prepareKapt(): Configuration {
        apply<Kapt3GradleSubplugin>()
        val kapt by configurations.getting {
            attributes {
                attribute(platformType, "jvm")
            }
        }
        return kapt
    }

    final override fun apply(project: Project): Unit = with(project) {
        apply<KotlinPluginWrapper>()

        tasks.withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        applyMCSPack()
    }
}
