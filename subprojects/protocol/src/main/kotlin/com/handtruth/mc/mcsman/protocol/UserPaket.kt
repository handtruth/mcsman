package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.*

open class UserPaket private constructor(type: Types) : Paket(), TypedPaket<UserPaket.Types> {
    final override val id = PaketID.User

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List, FindByRealName, FindByEMail, Create, ChangeRealName, ChangeEMail, Block, Unblock, Remove
    }

    class GetIdRequest(name: String) : UserPaket(Types.GetId) {
        var name by string(name)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest("")
        }
    }

    class GetIdResponse(user: Int, name: String) : UserPaket(Types.GetId) {
        var user by varInt(user)
        var name by string(name)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, "")
        }
    }

    class GetRequest(user: Int) : UserPaket(Types.Get) {
        var user by varInt(user)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        user: Int, name: String, realName: String, email: String, blocked: Boolean,
        groups: MutableList<Int>, ownedGroups: MutableList<Int>
    ) : UserPaket(Types.Get) {
        var user by varInt(user)
        var name by string(name)
        var realName by string(realName)
        var email by string(email)
        var blocked by bool(blocked)
        var groups by listOfVarInt(groups)
        var ownedGroups by listOfVarInt(ownedGroups)

        companion object : PaketCreator<GetResponse> {
            override fun produce() =
                GetResponse(-1, "", "", "", false, mutableListOf(), mutableListOf())
        }
    }

    object ListRequest : UserPaket(Types.List),
        PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(ids: MutableList<Int>, names: MutableList<String>) : UserPaket(Types.List) {
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf(), mutableListOf())
        }
    }

    class FindByRealNameRequest(realName: String) : UserPaket(Types.FindByRealName) {
        var realName by string(realName)

        companion object : PaketCreator<FindByRealNameRequest> {
            override fun produce() = FindByRealNameRequest("")
        }
    }

    class FindByRealNameResponse(
        realName: String, ids: MutableList<Int>, names: MutableList<String>
    ) : UserPaket(Types.FindByRealName) {
        var realName by string(realName)
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<FindByRealNameResponse> {
            override fun produce() = FindByRealNameResponse("", mutableListOf(), mutableListOf())
        }
    }

    class FindByEMailRequest(email: String) : UserPaket(Types.FindByEMail) {
        var email by string(email)

        companion object : PaketCreator<FindByEMailRequest> {
            override fun produce() = FindByEMailRequest("")
        }
    }

    class FindByEMailResponse(
        email: String, ids: MutableList<Int>, names: MutableList<String>
    ) : UserPaket(Types.FindByEMail) {
        var email by string(email)
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<FindByEMailResponse> {
            override fun produce() = FindByEMailResponse("", mutableListOf(), mutableListOf())
        }
    }

    class CreateRequest(name: String, realName: String, email: String) : UserPaket(Types.Create) {
        var name by string(name)
        var realName by string(realName)
        var email by string(email)

        companion object : PaketCreator<CreateRequest> {
            override fun produce() = CreateRequest("", "", "")
        }
    }

    class CreateResponse(user: Int, name: String) : UserPaket(Types.Create) {
        var user by varInt(user)
        var name by string(name)

        companion object : PaketCreator<CreateResponse> {
            override fun produce() = CreateResponse(-1, "")
        }
    }

    class ChangeRealNameRequest(user: Int, realName: String) : UserPaket(Types.ChangeRealName) {
        var user by varInt(user)
        var realName by string(realName)

        companion object : PaketCreator<ChangeRealNameRequest> {
            override fun produce() = ChangeRealNameRequest(-1, "")
        }
    }

    class ChangeEMailRequest(user: Int, email: String) : UserPaket(Types.ChangeEMail) {
        var user by varInt(user)
        var email by string(email)

        companion object : PaketCreator<ChangeEMailRequest> {
            override fun produce() = ChangeEMailRequest(-1, "")
        }
    }

    class BlockRequest(user: Int) : UserPaket(Types.Block) {
        var user by varInt(user)

        companion object : PaketCreator<BlockRequest> {
            override fun produce() = BlockRequest(-1)
        }
    }

    class UnblockRequest(user: Int) : UserPaket(Types.Unblock) {
        var user by varInt(user)

        companion object : PaketCreator<UnblockRequest> {
            override fun produce() = UnblockRequest(-1)
        }
    }

    class RemoveRequest(user: Int) : UserPaket(Types.Remove) {
        var user by varInt(user)

        companion object : PaketCreator<RemoveRequest> {
            override fun produce() = RemoveRequest(-1)
        }
    }

    companion object : PaketCreator<UserPaket> {
        override fun produce() = UserPaket(Types.GetId)
    }
}
