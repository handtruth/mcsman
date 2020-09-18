package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.listOfVarInt
import com.handtruth.mc.paket.fields.string
import com.handtruth.mc.paket.fields.varInt

open class BundlePaket private constructor(type: Types) : Paket(), TypedPaket<BundlePaket.Types> {
    final override val id = PaketID.Bundle

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List
    }

    class GetIdRequest(group: String, artifact: String) : BundlePaket(Types.GetId) {
        var group by string(group)
        var artifact by string(artifact)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest("", "")
        }
    }

    class GetIdResponse(bundle: Int, group: String, artifact: String) : BundlePaket(Types.GetId) {
        var bundle by varInt(bundle)
        var group by string(group)
        var artifact by string(artifact)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, "", "")
        }
    }

    class GetRequest(bundle: Int) : BundlePaket(Types.Get) {
        var bundle by varInt(bundle)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        bundle: Int, group: String, artifact: String, version: String, dependencies: MutableList<Int>
    ) : BundlePaket(Types.Get) {
        var bundle by varInt(bundle)
        var group by string(group)
        var artifact by string(artifact)
        var version by string(version)
        var dependencies by listOfVarInt(dependencies)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(-1, "", "", "", mutableListOf())
        }
    }

    object ListRequest : BundlePaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(ids: MutableList<Int>) : BundlePaket(Types.List) {
        var ids by listOfVarInt(ids)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf())
        }
    }

    companion object : PaketCreator<BundlePaket> {
        override fun produce() = BundlePaket(Types.GetId)
    }
}
