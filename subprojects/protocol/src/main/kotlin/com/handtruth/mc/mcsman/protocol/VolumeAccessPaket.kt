package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.*

open class VolumeAccessPaket private constructor(type: Types) : Paket(), TypedPaket<VolumeAccessPaket.Types> {
    final override val id = PaketID.VolumeAccess

    final override val type by enum(type)

    enum class Types {
        Check, ListForActor, ListForVolume, ListForActorAndVolume, Grant, Revoke, Elevate, Demote
    }

    class CheckRequest(
        agent: AgentTypes, actor: Int, volume: Int, level: VolumeAccessLevel
    ) : VolumeAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var level by enum(level)

        companion object : PaketCreator<CheckRequest> {
            override fun produce() =
                CheckRequest(AgentTypes.User, -1, -1, VolumeAccessLevel.Read)
        }
    }

    class CheckResponse(
        agent: AgentTypes, actor: Int, volume: Int, level: VolumeAccessLevel, allowed: Boolean
    ) : VolumeAccessPaket(Types.Check) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var level by enum(level)
        var allowed by bool(allowed)

        companion object : PaketCreator<CheckResponse> {
            override fun produce() =
                CheckResponse(AgentTypes.User, -1, -1, VolumeAccessLevel.Read, false)
        }
    }

    class ListForActorRequest(agent: AgentTypes, actor: Int) : VolumeAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)

        companion object : PaketCreator<ListForActorRequest> {
            override fun produce() = ListForActorRequest(AgentTypes.User, -1)
        }
    }

    class ListForActorResponse(
        agent: AgentTypes, actor: Int, volumes: MutableList<Int>, levels: MutableList<VolumeAccessLevel>
    ) : VolumeAccessPaket(Types.ListForActor) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volumes by listOfVarInt(volumes)
        var levels by listOfEnum(levels)

        companion object : PaketCreator<ListForActorResponse> {
            override fun produce() = ListForActorResponse(
                AgentTypes.User, -1, mutableListOf(), mutableListOf()
            )
        }
    }

    class ListForVolumeRequest(volume: Int) : VolumeAccessPaket(Types.ListForVolume) {
        var volume by varInt(volume)

        companion object : PaketCreator<ListForVolumeRequest> {
            override fun produce() = ListForVolumeRequest(-1)
        }
    }

    class ListForVolumeResponse(
        volume: Int, agents: MutableList<AgentTypes>, actors: MutableList<Int>, levels: MutableList<VolumeAccessLevel>
    ) : VolumeAccessPaket(Types.ListForVolume) {
        var volume by varInt(volume)
        var agents by listOfEnum(agents)
        var actors by listOfVarInt(actors)
        var levels by listOfEnum(levels)

        companion object : PaketCreator<ListForVolumeResponse> {
            override fun produce() =
                ListForVolumeResponse(-1, mutableListOf(), mutableListOf(), mutableListOf())
        }
    }

    class ListForActorAndVolumeRequest(
        agent: AgentTypes, actor: Int, volume: Int
    ) : VolumeAccessPaket(Types.ListForActorAndVolume) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)

        companion object : PaketCreator<ListForActorAndVolumeRequest> {
            override fun produce() =
                ListForActorAndVolumeRequest(AgentTypes.User, -1, -1)
        }
    }

    class ListForActorAndVolumeResponse(
        agent: AgentTypes, actor: Int, volume: Int, levels: MutableList<VolumeAccessLevel>
    ) : VolumeAccessPaket(Types.ListForActorAndVolume) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var levels by listOfEnum(levels)

        companion object : PaketCreator<ListForActorAndVolumeResponse> {
            override fun produce() =
                ListForActorAndVolumeResponse(AgentTypes.User, -1, -1, mutableListOf())
        }
    }

    class GrantRequest(
        agent: AgentTypes, actor: Int, volume: Int, level: VolumeAccessLevel
    ) : VolumeAccessPaket(Types.Grant) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var level by enum(level)

        companion object : PaketCreator<GrantRequest> {
            override fun produce() =
                GrantRequest(AgentTypes.User, -1, -1, VolumeAccessLevel.Read)
        }
    }

    class RevokeRequest(
        agent: AgentTypes, actor: Int, volume: Int, level: VolumeAccessLevel
    ) : VolumeAccessPaket(Types.Revoke) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var level by enum(level)

        companion object : PaketCreator<RevokeRequest> {
            override fun produce() =
                RevokeRequest(AgentTypes.User, -1, -1, VolumeAccessLevel.Read)
        }
    }

    class ElevateRequest(
        agent: AgentTypes, actor: Int, volume: Int, level: VolumeAccessLevel
    ) : VolumeAccessPaket(Types.Elevate) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var level by enum(level)

        companion object : PaketCreator<ElevateRequest> {
            override fun produce() =
                ElevateRequest(AgentTypes.User, -1, -1, VolumeAccessLevel.Read)
        }
    }

    class DemoteRequest(
        agent: AgentTypes, actor: Int, volume: Int, level: VolumeAccessLevel
    ) : VolumeAccessPaket(Types.Demote) {
        var agent by enum(agent)
        var actor by varInt(actor)
        var volume by varInt(volume)
        var level by enum(level)

        companion object : PaketCreator<DemoteRequest> {
            override fun produce() =
                DemoteRequest(AgentTypes.User, -1, -1, VolumeAccessLevel.Read)
        }
    }

    companion object : PaketCreator<VolumeAccessPaket> {
        override fun produce() = VolumeAccessPaket(Types.Check)
    }
}
