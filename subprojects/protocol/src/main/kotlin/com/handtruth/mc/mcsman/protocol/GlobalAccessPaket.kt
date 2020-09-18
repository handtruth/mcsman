package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.*

open class GlobalAccessPaket private constructor(type: Types) : Paket(), TypedPaket<GlobalAccessPaket.Types> {
    final override val id = PaketID.GlobalAccess

    final override val type by enum(type)

    enum class Types {
        Check, ListForActor, Grant, Revoke
    }

    class CheckRequest(agent: AgentTypes, actor: Int, permission: String) : GlobalAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var permission by string(permission)

        companion object : PaketCreator<CheckRequest> {
            override fun produce() = CheckRequest(AgentTypes.User, -1, "")
        }
    }

    class CheckResponse(
        agent: AgentTypes, actor: Int, permission: String, allowed: Boolean
    ) : GlobalAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var permission by string(permission)
        var allowed by bool(allowed)

        companion object : PaketCreator<CheckResponse> {
            override fun produce() = CheckResponse(AgentTypes.User, -1, "", false)
        }
    }

    class ListForActorRequest(agent: AgentTypes, actor: Int) : GlobalAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)

        companion object : PaketCreator<ListForActorRequest> {
            override fun produce() = ListForActorRequest(AgentTypes.User, -1)
        }
    }

    class ListForActorResponse(
        agent: AgentTypes, actor: Int, permissions: MutableList<String>, allowance: MutableList<Boolean>
    ) : GlobalAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var permissions by listOfString(permissions)
        var allowance by listOfBool(allowance)

        companion object : PaketCreator<ListForActorResponse> {
            override fun produce() = ListForActorResponse(
                AgentTypes.User, -1, mutableListOf(), mutableListOf()
            )
        }
    }

    class GrantRequest(
        agent: AgentTypes, actor: Int, permission: String, allowed: Boolean
    ) : GlobalAccessPaket(Types.Grant) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var permission by string(permission)
        var allowed by bool(allowed)

        companion object : PaketCreator<GrantRequest> {
            override fun produce() = GrantRequest(AgentTypes.User, -1, "", false)
        }
    }

    class RevokeRequest(
        agent: AgentTypes, actor: Int, permission: String, allowed: Boolean
    ) : GlobalAccessPaket(Types.Revoke) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var permission by string(permission)
        var allowed by bool(allowed)

        companion object : PaketCreator<RevokeRequest> {
            override fun produce() = RevokeRequest(AgentTypes.User, -1, "", false)
        }
    }

    companion object : PaketCreator<GlobalAccessPaket> {
        override fun produce() = GlobalAccessPaket(Types.ListForActor)
    }
}
