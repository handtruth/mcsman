package com.handtruth.mc.mcsman.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName

sealed class MCSPackExtension(val type: String) {
    object Types {
        const val Bundle = "bundle"
        const val Client = "client"
        const val MCSDroid = "mcsdroid"
        const val MCSBot = "mcsbot"
        const val Protocol = "protocol"
        const val Common = "common"
        const val ClientPaket = "client-paket"
        const val ClientHTTP = "client-http"
        const val ClientGUI = "client-gui"
        const val MCSWeb = "mcsweb"
    }
}

open class MCSPackBundleExtension : MCSPackExtension(Types.Bundle) {
    private val _artifacts: MutableList<ModuleArtifact<*>> = arrayListOf()
    val artifacts: List<ModuleArtifact<*>> get() = _artifacts
    fun artifact(source: Project, module: String? = null, type: String? = null): ModuleArtifact<Project> {
        val resultType = type ?: source.extensions.getByName<MCSPackExtension>("mcspack").type
        val artifact = ModuleArtifact(source, module, resultType)
        _artifacts += artifact
        return artifact
    }
}

open class MCSPackClientExtension : MCSPackExtension(Types.Client)
open class MCSPackMCSDroidExtension : MCSPackExtension(Types.MCSDroid)
open class MCSPackMCSBotExtension : MCSPackExtension(Types.MCSBot)
open class MCSPackProtocolExtension : MCSPackExtension(Types.Protocol)
open class MCSPackCommonExtension : MCSPackExtension(Types.Common)
open class MCSPackClientPaketExtension : MCSPackExtension(Types.ClientPaket)
open class MCSPackClientMCSWebExtension : MCSPackExtension(Types.ClientHTTP)
open class MCSPackClientGUIExtension : MCSPackExtension(Types.ClientGUI)
open class MCSPackMCSWebExtension : MCSPackExtension(Types.MCSWeb)

data class ModuleArtifact<out T : Any>(val source: T, val module: String?, val type: String)
