package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.bool
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.listOfVarInt
import com.handtruth.mc.paket.fields.varInt

open class SessionPaket private constructor(type: Types) : Paket(), TypedPaket<SessionPaket.Types> {
    final override val id = PaketID.Session

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List, Upgrade, Downgrade, Disconnect
    }

    object GetIdRequest : SessionPaket(Types.GetId), PaketSingleton<GetIdRequest> {
        override fun produce() = this
    }

    class GetIdResponse(session: Int) : SessionPaket(Types.GetId) {
        var session by varInt(session)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1)
        }
    }

    class GetRequest(session: Int) : SessionPaket(Types.Get) {
        val session by varInt(session)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        session: Int,
        privileged: Boolean,
        agent: AgentTypes,
        actor: Int,
        modules: MutableList<Int>,
        servers: MutableList<Int>,
        services: MutableList<Int>,
        listenEvents: Boolean
    ) : SessionPaket(Types.Get) {
        var session by varInt(session)
        var privileged by bool(privileged)
        var agent by enum(agent)
        var actor by varInt(actor)
        var modules by listOfVarInt(modules)
        var servers by listOfVarInt(servers)
        var services by listOfVarInt(services)
        var listenEvents by bool(listenEvents)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(
                -1, false, AgentTypes.User, -1,
                mutableListOf(), mutableListOf(), mutableListOf(), false
            )
        }
    }

    object ListRequest : SessionPaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(sessions: MutableList<Int>) : SessionPaket(Types.List) {
        var sessions by listOfVarInt(sessions)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf())
        }
    }

    object UpgradeRequest : SessionPaket(Types.Upgrade), PaketSingleton<UpgradeRequest> {
        override fun produce() = this
    }

    object DowngradeRequest : SessionPaket(Types.Downgrade), PaketSingleton<DowngradeRequest> {
        override fun produce() = this
    }

    class DisconnectRequest(session: Int) : SessionPaket(Types.Disconnect) {
        var session by varInt(session)

        companion object : PaketCreator<DisconnectRequest> {
            override fun produce() = DisconnectRequest(-1)
        }
    }

    companion object : PaketCreator<SessionPaket> {
        override fun produce() = SessionPaket(Types.GetId)
    }
}
