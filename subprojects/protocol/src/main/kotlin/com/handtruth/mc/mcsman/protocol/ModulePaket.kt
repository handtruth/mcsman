package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.*

open class ModulePaket private constructor(type: Types) : Paket(), TypedPaket<ModulePaket.Types> {
    final override val id = PaketID.Module

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List, Artifacts
    }

    class GetIdRequest(name: String) : ModulePaket(Types.GetId) {
        var name by string(name)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest("")
        }
    }

    class GetIdResponse(module: Int, name: String) : ModulePaket(Types.GetId) {
        var module by varInt(module)
        var name by string(name)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, "")
        }
    }

    class GetRequest(module: Int) : ModulePaket(Types.Get) {
        var module by varInt(module)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        module: Int, name: String, bundle: Int, enabled: Boolean, depends: MutableList<Int>
    ) : ModulePaket(Types.Get) {
        var module by varInt(module)
        var name by string(name)
        var bundle by varInt(bundle)
        var enabled by bool(enabled)
        var depends by listOfVarInt(depends)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(-1, "", -1, false, mutableListOf())
        }
    }

    object ListRequest : ModulePaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(ids: MutableList<Int>, names: MutableList<String>) : ModulePaket(Types.List) {
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf(), mutableListOf())
        }
    }

    class ArtifactsRequest(
        module: Int, artifactType: String, `class`: String, platform: String
    ) : ModulePaket(Types.Artifacts) {
        var module by varInt(module)
        var artifactType by string(artifactType)
        var `class` by string(`class`)
        var platform by string(platform)

        companion object : PaketCreator<ArtifactsRequest> {
            override fun produce() = ArtifactsRequest(-1, "", "", "")
        }
    }

    class ArtifactsResponse(
        module: Int, artifactTypes: MutableList<String>, classes: MutableList<String>, platforms: MutableList<String>,
        uris: MutableList<String>
    ) : ModulePaket(Types.Artifacts) {
        var module by varInt(module)
        var artifactTypes by listOfString(artifactTypes)
        var classes by listOfString(classes)
        var platforms by listOfString(platforms)
        var uris by listOfString(uris)

        companion object : PaketCreator<ArtifactsResponse> {
            override fun produce() = ArtifactsResponse(
                -1, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()
            )
        }
    }

    companion object : PaketCreator<ModulePaket> {
        override fun produce() = ModulePaket(Types.GetId)
    }
}
