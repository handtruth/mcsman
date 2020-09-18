package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.*

open class ServerAccessPaket private constructor(type: Types) : Paket(), TypedPaket<ServerAccessPaket.Types> {
    final override val id = PaketID.ServerAccess

    final override val type by enum(type)

    enum class Types {
        Check, ListForActor, ListForServer, ListForActorAndServer, Grant, Revoke
    }

    class CheckRequest(
        agent: AgentTypes, actor: Int, server: Int, permission: String
    ) : ServerAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var server by varInt(server)
        var permission by string(permission)

        companion object : PaketCreator<CheckRequest> {
            override fun produce() = CheckRequest(AgentTypes.User, -1, -1, "")
        }
    }

    class CheckResponse(
        agent: AgentTypes, actor: Int, server: Int, permission: String, allowed: Boolean
    ) : ServerAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var server by varInt(server)
        var permission by string(permission)
        var allowed by bool(allowed)

        companion object : PaketCreator<CheckResponse> {
            override fun produce() =
                CheckResponse(AgentTypes.User, -1, -1, "", false)
        }
    }

    class ListForActorRequest(agent: AgentTypes, actor: Int) : ServerAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)

        companion object : PaketCreator<ListForActorRequest> {
            override fun produce() = ListForActorRequest(AgentTypes.User, -1)
        }
    }

    class ListForActorResponse(
        agent: AgentTypes, actor: Int, servers: MutableList<Int>,
        permissions: MutableList<String>, allowance: MutableList<Boolean>
    ) : ServerAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var servers by listOfVarInt(servers)
        var permissions by listOfString(permissions)
        var allowance by listOfBool(allowance)

        companion object : PaketCreator<ListForActorResponse> {
            override fun produce() = ListForActorResponse(
                AgentTypes.User, -1, mutableListOf(), mutableListOf(), mutableListOf()
            )
        }
    }

    class ListForServerRequest(server: Int) : ServerAccessPaket(Types.ListForServer) {
        var server by varInt(server)

        companion object : PaketCreator<ListForServerRequest> {
            override fun produce() = ListForServerRequest(-1)
        }
    }

    class ListForServerResponse(
        server: Int, agents: MutableList<AgentTypes>, actors: MutableList<Int>,
        permissions: MutableList<String>, allowance: MutableList<Boolean>
    ) : ServerAccessPaket(Types.ListForServer) {
        var server by varInt(server)
        var agents by listOfEnum(agents)
        var actors by listOfVarInt(actors)
        var permissions by listOfString(permissions)
        var allowance by listOfBool(allowance)

        companion object : PaketCreator<ListForServerResponse> {
            override fun produce() =
                ListForServerResponse(-1, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
        }
    }

    class ListForActorAndServerRequest(
        agent: AgentTypes, actor: Int, server: Int
    ) : ServerAccessPaket(Types.ListForActorAndServer) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var server by varInt(server)

        companion object : PaketCreator<ListForActorAndServerRequest> {
            override fun produce() =
                ListForActorAndServerRequest(AgentTypes.User, -1, -1)
        }
    }

    class ListForActorAndServerResponse(
        agent: AgentTypes, actor: Int, server: Int, permissions: MutableList<String>, allowance: MutableList<Boolean>
    ) : ServerAccessPaket(Types.ListForActorAndServer) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var server by varInt(server)
        var permissions by listOfString(permissions)
        var allowance by listOfBool(allowance)

        companion object : PaketCreator<ListForActorAndServerResponse> {
            override fun produce() =
                ListForActorAndServerResponse(AgentTypes.User, -1, -1, mutableListOf(), mutableListOf())
        }
    }

    class GrantRequest(
        agent: AgentTypes, actor: Int, server: Int, permission: String, allowed: Boolean
    ) : ServerAccessPaket(Types.Grant) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var server by varInt(server)
        var permission by string(permission)
        var allowed by bool(allowed)

        companion object : PaketCreator<GrantRequest> {
            override fun produce() =
                GrantRequest(AgentTypes.User, -1, -1, "", false)
        }
    }

    class RevokeRequest(
        agent: AgentTypes, actor: Int, server: Int, permission: String, allowed: Boolean
    ) : ServerAccessPaket(Types.Revoke) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var server by varInt(server)
        var permission by string(permission)
        var allowed by bool(allowed)

        companion object : PaketCreator<RevokeRequest> {
            override fun produce() =
                RevokeRequest(AgentTypes.User, -1, -1, "", false)
        }
    }

    companion object : PaketCreator<ServerAccessPaket> {
        override fun produce() = ServerAccessPaket(Types.Check)
    }
}
