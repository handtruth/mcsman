package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.string

open class AuthorizationPaket(actor: AgentTypes, method: String) : Paket() {
    override val id = PaketID.Authorization

    var actor by enum(actor)
    val method by string(method)

    companion object : PaketCreator<AuthorizationPaket> {
        override fun produce() = AuthorizationPaket(AgentTypes.User, "")
    }

    class Password(login: String, password: String, actor: AgentTypes = AgentTypes.User) :
        AuthorizationPaket(actor, "password") {

        var login by string(login)
        var password by string(password)

        companion object : PaketCreator<Password> {
            override fun produce() = Password("system", "")
        }
    }

    open class IPAddress(actor: AgentTypes = AgentTypes.Service) : AuthorizationPaket(actor, "ipaddress") {
        companion object : PaketCreator<IPAddress> {
            override fun produce() = IPAddress()
        }
    }

    class Token(token: String = "", actor: AgentTypes = AgentTypes.User) : AuthorizationPaket(actor, "token") {

        var token by string(token)

        companion object : PaketCreator<Token> {
            override fun produce() = Token("")
        }

    }
}
