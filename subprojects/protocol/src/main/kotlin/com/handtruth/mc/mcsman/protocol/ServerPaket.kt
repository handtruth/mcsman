package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.*

open class ServerPaket private constructor(type: Types) : Paket(), TypedPaket<ServerPaket.Types> {
    final override val id = PaketID.Server

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List, Status, Create, Manage, Command, Listen,
        Mute, ChangeDescription, Upgrade, Remove
    }

    class GetIdRequest(name: String) : ServerPaket(Types.GetId) {
        var name by string(name)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest("")
        }
    }

    class GetIdResponse(server: Int, name: String) : ServerPaket(Types.GetId) {
        var server by varInt(server)
        var name by string(name)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, "")
        }
    }

    class GetRequest(server: Int) : ServerPaket(Types.Get) {
        var server by varInt(server)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        server: Int, name: String, status: ExecutableStatus, imageId: String, image: String,
        game: String, description: String, volumes: MutableList<Int>
    ) : ServerPaket(Types.Get) {
        var server by varInt(server)
        var name by string(name)
        var status by enum(status)
        var imageId by string(imageId)
        var image by string(image)
        var game by string(game)
        var description by string(description)
        var volumes by listOfVarInt(volumes)

        constructor(
            server: Int, name: String, status: ExecutableStatus, imageId: String, image: String,
            game: String, description: ChatMessage, volumes: MutableList<Int>
        ) : this(
            server, name, status, imageId, image, game, description.toChatString(), volumes
        )

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(
                -1, "", ExecutableStatus.Stopped, "", "", "",
                "", mutableListOf()
            )
        }
    }

    object ListRequest : ServerPaket(Types.List), PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(ids: MutableList<Int>, names: MutableList<String>) : ServerPaket(Types.List) {
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf(), mutableListOf())
        }
    }

    class StatusRequest(server: Int) : ServerPaket(Types.Status) {
        var server by varInt(server)

        companion object : PaketCreator<StatusRequest> {
            override fun produce() = StatusRequest(-1)
        }
    }

    class StatusResponse(server: Int, status: ExecutableStatus) : ServerPaket(Types.Status) {
        var server by varInt(server)
        var status by enum(status)

        companion object : PaketCreator<StatusResponse> {
            override fun produce() = StatusResponse(-1, ExecutableStatus.Stopped)
        }
    }

    class CreateRequest(name: String, image: String, description: String) : ServerPaket(Types.Create) {
        var name by string(name)
        var image by string(image)
        var description by string(description)

        constructor(name: String, image: String, description: ChatMessage) : this(
            name, image, description.toChatString()
        )

        companion object : PaketCreator<CreateRequest> {
            override fun produce() = CreateRequest("", "", "")
        }
    }

    class CreateResponse(server: Int, name: String) : ServerPaket(Types.Create) {
        var server by varInt(server)
        var name by string(name)

        companion object : PaketCreator<CreateResponse> {
            override fun produce() = CreateResponse(-1, "")
        }
    }

    class ManageRequest(server: Int, action: ExecutableActions) : ServerPaket(Types.Manage) {
        var server by varInt(server)
        var action by enum(action)

        companion object : PaketCreator<ManageRequest> {
            override fun produce() = ManageRequest(-1, ExecutableActions.Start)
        }
    }

    class CommandRequest(server: Int, command: String) : ServerPaket(Types.Command) {
        var server by varInt(server)
        var command by string(command)

        companion object : PaketCreator<CommandRequest> {
            override fun produce() = CommandRequest(-1, "")
        }
    }

    class ListenRequest(server: Int) : ServerPaket(Types.Listen) {
        var server by varInt(server)

        companion object : PaketCreator<ListenRequest> {
            override fun produce() = ListenRequest(-1)
        }
    }

    class MuteRequest(server: Int) : ServerPaket(Types.Mute) {
        var server by varInt(server)

        companion object : PaketCreator<MuteRequest> {
            override fun produce() = MuteRequest(-1)
        }
    }

    class ChangeDescriptionRequest(server: Int, description: String) : ServerPaket(Types.ChangeDescription) {
        var server by varInt(server)
        var description by string(description)

        constructor(server: Int, description: ChatMessage) : this(server, description.toChatString())

        companion object : PaketCreator<ChangeDescriptionRequest> {
            override fun produce() = ChangeDescriptionRequest(-1, "")
        }
    }

    class UpgradeRequest(server: Int) : ServerPaket(Types.Upgrade) {
        var server by varInt(server)

        companion object : PaketCreator<UpgradeRequest> {
            override fun produce() = UpgradeRequest(-1)
        }
    }

    class RemoveRequest(server: Int) : ServerPaket(Types.Remove) {
        var server by varInt(server)

        companion object : PaketCreator<RemoveRequest> {
            override fun produce() = RemoveRequest(-1)
        }
    }

    companion object : PaketCreator<ServerPaket> {
        override fun produce() = ServerPaket(Types.GetId)
    }
}
