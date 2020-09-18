@file:Suppress("UNUSED_VARIABLE")

package com.handtruth.mc.mcsman.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin

class MCSPackCommonPlugin : MCSPackPlugin() {
    override fun Project.applyMCSPack() {
        apply<SerializationGradleSubplugin>()
        val kapt = prepareKapt()

        val mcspackExt = extensions.create<MCSPackCommonExtension>("mcspack")

        val api by configurations.getting
        val runtime by configurations.getting
        val implementation by configurations.getting

        dependencies {
            val mcsmanPlatform = platform(mcsman("bom:${this@MCSPackCommonPlugin.version}"))
            val handtruthPlatform = platform("com.handtruth.internal:platform:$handtruthPlatformVersion")
            kapt(mcsmanPlatform); kapt(handtruthPlatform)
            runtime(mcsmanPlatform); runtime(handtruthPlatform)
            implementation(mcsmanPlatform); implementation(handtruthPlatform)
            api(mcsman("event"))
            kapt(mcsman("compiler-event"))
        }
    }
}
