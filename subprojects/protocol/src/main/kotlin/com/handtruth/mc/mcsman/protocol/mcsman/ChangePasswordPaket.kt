package com.handtruth.mc.mcsman.protocol.mcsman

import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.string
import com.handtruth.mc.paket.fields.varInt

sealed class ChangePasswordPaket : Paket() {
    final override val id = MCSManPaketID.ChangePassword

    class Request(password: String, agent: AgentTypes = AgentTypes.User, actor: Int = 0) : ChangePasswordPaket() {
        val password by string(password)
        val agent by enum(agent)
        val actor by varInt(actor)

        companion object : PaketCreator<Request> {
            override fun produce() = Request("")
        }
    }

    class Response(code: Codes, message: String) : ChangePasswordPaket() {
        val code by enum(code)
        val message by string(message)

        enum class Codes {
            Success, AccessDenied, Unsupported, Unknown
        }

        companion object : PaketCreator<Response> {
            override fun produce() = Response(Codes.Success,"")
            val Success = produce()
        }
    }
}
