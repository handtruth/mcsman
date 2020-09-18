package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.event.EventTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.listOfString
import com.handtruth.mc.paket.fields.string
import com.handtruth.mc.paket.fields.varInt

open class EventPaket private constructor(type: Types) : Paket(), TypedPaket<EventPaket.Types> {
    final override val id = PaketID.Event

    final override val type by enum(type)

    enum class Types {
        Get, List, Listen, Mute
    }

    class GetRequest(className: String) : EventPaket(Types.Get) {
        var className by string(className)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest("")
        }
    }

    class GetResponse(className: String, bundle: Int, eventTypes: EventTypes, implements: MutableList<String>) : EventPaket(Types.Get) {
        var className by string(className)
        var bundle by varInt(bundle)
        var eventTypes by enum(eventTypes)
        var implements by listOfString(implements)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse("", -1, EventTypes.Root, mutableListOf())
        }
    }

    object ListRequest : EventPaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(names: MutableList<String>) : EventPaket(Types.List) {
        var names by listOfString(names)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf())
        }
    }

    object ListenRequest : EventPaket(Types.Listen), PaketSingleton<ListenRequest> {
        override fun produce() = this
    }

    object MuteRequest : EventPaket(Types.Mute), PaketSingleton<MuteRequest> {
        override fun produce() = this
    }

    companion object : PaketCreator<EventPaket> {
        override fun produce() = EventPaket(Types.Get)
    }
}
