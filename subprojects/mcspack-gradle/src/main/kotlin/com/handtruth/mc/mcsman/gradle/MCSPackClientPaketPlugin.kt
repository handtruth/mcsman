@file:Suppress("UNUSED_VARIABLE")

package com.handtruth.mc.mcsman.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting

class MCSPackClientPaketPlugin : MCSPackPlugin() {
    override fun Project.applyMCSPack() {
        val mcspackExt = extensions.create<MCSPackClientPaketExtension>("mcspack")

        val api by configurations.getting
        val implementation by configurations.getting
        val runtime by configurations.getting

        dependencies {
            val mcsmanPlatform = platform(mcsman("bom:${this@MCSPackClientPaketPlugin.version}"))
            val handtruthPlatform = platform("com.handtruth.internal:platform:$handtruthPlatformVersion")
            runtime(mcsmanPlatform); runtime(handtruthPlatform)
            implementation(mcsmanPlatform); implementation(handtruthPlatform)
            api(mcsman("client-paket"))
            api(mcsman("protocol"))
        }
    }
}
