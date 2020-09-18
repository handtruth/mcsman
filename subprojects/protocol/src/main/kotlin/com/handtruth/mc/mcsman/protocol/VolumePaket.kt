package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.listOfVarInt
import com.handtruth.mc.paket.fields.string
import com.handtruth.mc.paket.fields.varInt

open class VolumePaket private constructor(type: Types): Paket(), TypedPaket<VolumePaket.Types> {
    final override val id = PaketID.Volume

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List
    }

    class GetIdRequest(server: Int, name: String) : VolumePaket(Types.GetId) {
        var server by varInt(server)
        var name by string(name)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest(-1, "")
        }
    }

    class GetIdResponse(volume: Int, server: Int, name: String) : VolumePaket(Types.GetId) {
        var volume by varInt(volume)
        var server by varInt(server)
        var name by string(name)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, -1, "")
        }
    }

    class GetRequest(volume: Int) : VolumePaket(Types.Get) {
        var volume by varInt(volume)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(volume: Int, name: String, server: Int) : VolumePaket(Types.Get) {
        var volume by varInt(volume)
        var name by string(name)
        var server by varInt(server)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(-1, "", -1)
        }
    }

    object ListRequest : VolumePaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(volumes: MutableList<Int>) : VolumePaket(Types.List) {
        var volumes by listOfVarInt(volumes)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf())
        }
    }

    companion object : PaketCreator<VolumePaket> {
        override fun produce() = VolumePaket(Types.GetId)
    }
}
