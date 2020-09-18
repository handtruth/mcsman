package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.*

open class ServicePaket private constructor(type: Types): Paket(), TypedPaket<ServicePaket.Types> {
    final override val id = PaketID.Service

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List, Status, Manage, Command, Listen, Mute
    }

    class GetIdRequest(name: String) : ServicePaket(Types.GetId) {
        var name by string(name)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest("")
        }
    }

    class GetIdResponse(service: Int, name: String) : ServicePaket(Types.GetId) {
        var service by varInt(service)
        var name by string(name)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, "")
        }
    }

    class GetRequest(service: Int) : ServicePaket(Types.Get) {
        var service by varInt(service)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        service: Int, name: String, bundle: Int, status: ExecutableStatus, factory: String
    ) : ServicePaket(Types.Get) {
        var service by varInt(service)
        var name by string(name)
        var bundle by varInt(bundle)
        var status by enum(status)
        var factory by string(factory)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(-1, "", -1, ExecutableStatus.Stopped, "")
        }
    }

    object ListRequest : ServicePaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(ids: MutableList<Int>, names: MutableList<String>) : ServicePaket(Types.List) {
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf(), mutableListOf())
        }
    }

    class StatusRequest(service: Int) : ServicePaket(Types.Status) {
        var service by varInt(service)

        companion object : PaketCreator<StatusRequest> {
            override fun produce() = StatusRequest(-1)
        }
    }

    class StatusResponse(server: Int, status: ExecutableStatus) : ServicePaket(Types.Status) {
        var server by varInt(server)
        var status by enum(status)

        companion object : PaketCreator<StatusResponse> {
            override fun produce() = StatusResponse(-1, ExecutableStatus.Stopped)
        }
    }

    class ManageRequest(service: Int, action: ExecutableActions) : ServicePaket(Types.Manage) {
        var service by varInt(service)
        var action by enum(action)

        companion object : PaketCreator<ManageRequest> {
            override fun produce() = ManageRequest(-1, ExecutableActions.Start)
        }
    }

    class CommandRequest(service: Int, command: String) : ServicePaket(Types.Command) {
        var service by varInt(service)
        var command by string(command)

        companion object : PaketCreator<CommandRequest> {
            override fun produce() = CommandRequest(-1, "")
        }
    }

    class ListenRequest(service: Int) : ServicePaket(Types.Listen) {
        var service by varInt(service)

        companion object : PaketCreator<ListenRequest> {
            override fun produce() = ListenRequest(-1)
        }
    }

    class MuteRequest(service: Int) : ServicePaket(Types.Mute) {
        var service by varInt(service)

        companion object : PaketCreator<MuteRequest> {
            override fun produce() = MuteRequest(-1)
        }
    }

    companion object : PaketCreator<ServicePaket> {
        override fun produce() = ServicePaket(Types.GetId)
    }
}
