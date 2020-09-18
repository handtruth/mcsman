pluginManagement {
    repositories {
        mavenLocal()
        maven("https://mvn.handtruth.com")
        maven("https://dl.bintray.com/pdvrieze/maven")
        gradlePluginPortal()
    }
    val kotlinVersion: String by settings
    val gitAndroidVersion: String by settings
    val atomicfuVersion: String by settings
    val gradlePublishPlugin: String by settings
    val shadowJarVersion: String by settings
    resolutionStrategy {
        eachPlugin {
            when {
                requested.id.id.startsWith("org.jetbrains.kotlin") -> useVersion(kotlinVersion)
                requested.id.id == "kotlinx-atomicfu" ->
                    useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfuVersion")
                requested.id.id.startsWith("com.handtruth.mc.mcsman") ->
                    useVersion("untagged-64-8cadbf6-mcsman3-dirty")
            }
        }
    }
    @Suppress("UnstableApiUsage")
    plugins {
        id("com.gladed.androidgitversion") version gitAndroidVersion
        id("com.gradle.plugin-publish") version gradlePublishPlugin
        id("com.github.johnrengelman.shadow") version shadowJarVersion
    }
}

rootProject.name = "example"

fun subproject(name: String) {
    val projectName = ":example-$name"
    include(projectName)
    project(projectName).projectDir = file("subprojects/$name")
}

subproject("client")
subproject("common")
subproject("bundle")
