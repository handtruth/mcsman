pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    val kotlinVersion: String by settings
    val gitAndroidVersion: String by settings
    val atomicfuVersion: String by settings
    val gradlePublishPlugin: String by settings
    val shadowJarVersion: String by settings
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("org.jetbrains.kotlin"))
                useVersion(kotlinVersion)
            else if (requested.id.id == "kotlinx-atomicfu")
                useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfuVersion")
        }
    }
    @Suppress("UnstableApiUsage")
    plugins {
        id("com.gladed.androidgitversion") version gitAndroidVersion
        id("com.gradle.plugin-publish") version gradlePublishPlugin
        id("com.github.johnrengelman.shadow") version shadowJarVersion
    }
}

rootProject.name = "mcsman"

fun subproject(name: String) {
    val projectName = ":mcsman-$name"
    include(projectName)
    project(projectName).projectDir = file("subprojects/$name")
}

fun subproject(name: String, part: String) {
    val projectName = ":mcsman-$part-$name"
    include(projectName)
    project(projectName).projectDir = file("subprojects/$part/$name")
}

sequenceOf(
    "bom",
    "mcspack-gradle",
    "protocol",
    "core",
    "base",
    "event",
    "event-table-base",
    "compiler-base",
    "compiler-event",
    "compiler-event-table",
    "compiler-bundle",
    "client",
    "client-paket",
    "client-gui",
    "test"
).forEach { subproject(it) }

sequenceOf(
    "minecraft-server"
).forEach { subproject(it, "module") }

include("docker-client")
