package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.*

open class GroupPaket private constructor(type: Types) : Paket(), TypedPaket<GroupPaket.Types> {
    final override val id = PaketID.Group

    final override val type by enum(type)

    enum class Types {
        GetId, Get, List, FindByRealName, FindByOwner, Create,
        ChangeRealName, ChangeOwner, AddMember, RemoveMember, Remove
    }

    class GetIdRequest(name: String) : GroupPaket(Types.GetId) {
        var name by string(name)

        companion object : PaketCreator<GetIdRequest> {
            override fun produce() = GetIdRequest("")
        }
    }

    class GetIdResponse(group: Int, name: String) : GroupPaket(Types.GetId) {
        var group by varInt(group)
        var name by string(name)

        companion object : PaketCreator<GetIdResponse> {
            override fun produce() = GetIdResponse(-1, "")
        }
    }

    class GetRequest(group: Int) : GroupPaket(Types.Get) {
        var group by varInt(group)

        companion object : PaketCreator<GetRequest> {
            override fun produce() = GetRequest(-1)
        }
    }

    class GetResponse(
        group: Int, name: String, realName: String, owner: Int, members: MutableList<Int>
    ) : GroupPaket(Types.Get) {
        var group by varInt(group)
        var name by string(name)
        var realName by string(realName)
        var owner by varInt(owner)
        var members by listOfVarInt(members)

        companion object : PaketCreator<GetResponse> {
            override fun produce() = GetResponse(-1, "", "", -1, mutableListOf())
        }
    }

    object ListRequest : GroupPaket(Types.List),
        PaketSingleton<ListRequest> {
        override fun produce() = this
    }

    class ListResponse(ids: MutableList<Int>, names: MutableList<String>) : GroupPaket(Types.List) {
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<ListResponse> {
            override fun produce() = ListResponse(mutableListOf(), mutableListOf())
        }
    }

    class FindByRealNameRequest(realName: String) : GroupPaket(Types.FindByRealName) {
        var realName by string(realName)

        companion object : PaketCreator<FindByRealNameRequest> {
            override fun produce() = FindByRealNameRequest("")
        }
    }

    class FindByRealNameResponse(
        realName: String, ids: MutableList<Int>, names: MutableList<String>
    ) : GroupPaket(Types.FindByRealName) {
        var realName by string(realName)
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<FindByRealNameResponse> {
            override fun produce() = FindByRealNameResponse("", mutableListOf(), mutableListOf())
        }
    }

    class FindByOwnerRequest(user: Int) : GroupPaket(Types.FindByOwner) {
        var user by varInt(user)

        companion object : PaketCreator<FindByOwnerRequest> {
            override fun produce() = FindByOwnerRequest(-1)
        }
    }

    class FindByOwnerResponse(
        owner: Int, ids: MutableList<Int>, names: MutableList<String>
    ) : GroupPaket(Types.FindByOwner) {
        var owner by varInt(owner)
        var ids by listOfVarInt(ids)
        var names by listOfString(names)

        companion object : PaketCreator<FindByOwnerResponse> {
            override fun produce() = FindByOwnerResponse(-1, mutableListOf(), mutableListOf())
        }
    }

    class CreateRequest(name: String, realName: String) : GroupPaket(Types.Create) {
        var name by string(name)
        var realName by string(realName)

        companion object : PaketCreator<CreateRequest> {
            override fun produce() = CreateRequest("", "")
        }
    }

    class CreateResponse(group: Int, name: String) : GroupPaket(Types.Create) {
        var group by varInt(group)
        var name by string(name)

        companion object : PaketCreator<CreateResponse> {
            override fun produce() = CreateResponse(-1, "")
        }
    }

    class ChangeRealNameRequest(group: Int, realName: String) : GroupPaket(Types.ChangeRealName) {
        var group by varInt(group)
        var realName by string(realName)

        companion object : PaketCreator<ChangeRealNameRequest> {
            override fun produce() = ChangeRealNameRequest(-1, "")
        }
    }

    class ChangeOwnerRequest(group: Int, user: Int) : GroupPaket(Types.ChangeOwner) {
        var group by varInt(group)
        var user by varInt(user)

        companion object : PaketCreator<ChangeOwnerRequest> {
            override fun produce() = ChangeOwnerRequest(-1, -1)
        }
    }

    class AddMemberRequest(group: Int, user: Int) : GroupPaket(Types.AddMember) {
        var group by varInt(group)
        var user by varInt(user)

        companion object : PaketCreator<AddMemberRequest> {
            override fun produce() = AddMemberRequest(-1, -1)
        }
    }

    class RemoveMemberRequest(group: Int, user: Int) : GroupPaket(Types.RemoveMember) {
        var group by varInt(group)
        var user by varInt(user)

        companion object : PaketCreator<RemoveMemberRequest> {
            override fun produce() = RemoveMemberRequest(-1, -1)
        }
    }

    class RemoveRequest(group: Int) : GroupPaket(Types.Remove) {
        var group by varInt(group)

        companion object : PaketCreator<RemoveRequest> {
            override fun produce() = RemoveRequest(-1)
        }
    }

    companion object : PaketCreator<GroupPaket> {
        override fun produce() = GroupPaket(Types.GetId)
    }
}
