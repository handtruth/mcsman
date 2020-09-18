package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.*

open class ImageAccessPaket private constructor(type: Types) : Paket(), TypedPaket<ImageAccessPaket.Types> {
    final override val id = PaketID.ImageAccess

    final override val type by enum(type)

    enum class Types {
        Check, ListForActor, Grant, Revoke
    }

    class CheckRequest(agent: AgentTypes, actor: Int, image: String) : ImageAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var image by string(image)

        companion object : PaketCreator<CheckRequest> {
            override fun produce() = CheckRequest(AgentTypes.User, -1, "")
        }
    }

    class CheckResponse(
        agent: AgentTypes, actor: Int, image: String, allowed: Boolean
    ) : ImageAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var image by string(image)
        var allowed by bool(allowed)

        companion object : PaketCreator<CheckResponse> {
            override fun produce() = CheckResponse(AgentTypes.User, -1, "", false)
        }
    }

    class ListForActorRequest(agent: AgentTypes, actor: Int) : ImageAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)

        companion object : PaketCreator<ListForActorRequest> {
            override fun produce() = ListForActorRequest(AgentTypes.User, -1)
        }
    }

    class ListForActorResponse(
        agent: AgentTypes, actor: Int, wildcards: MutableList<String>, allowance: MutableList<Boolean>
    ) : ImageAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var wildcards by listOfString(wildcards)
        var allowance by listOfBool(allowance)

        companion object : PaketCreator<ListForActorResponse> {
            override fun produce() = ListForActorResponse(
                AgentTypes.User, -1, mutableListOf(), mutableListOf()
            )
        }
    }

    class GrantRequest(
        agent: AgentTypes, actor: Int, wildcard: String, allowed: Boolean
    ) : ImageAccessPaket(Types.Grant) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var wildcard by string(wildcard)
        var allowed by bool(allowed)

        companion object : PaketCreator<GrantRequest> {
            override fun produce() = GrantRequest(AgentTypes.User, -1, "", false)
        }
    }

    class RevokeRequest(
        agent: AgentTypes, actor: Int, wildcard: String, allowed: Boolean
    ) : ImageAccessPaket(Types.Revoke) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var wildcard by string(wildcard)
        var allowed by bool(allowed)

        companion object : PaketCreator<RevokeRequest> {
            override fun produce() = RevokeRequest(AgentTypes.User, -1, "", false)
        }
    }

    companion object : PaketCreator<ImageAccessPaket> {
        override fun produce() = ImageAccessPaket(Types.ListForActor)
    }
}
