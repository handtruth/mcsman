package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.string

class ErrorPaket(code: ErrorCodes, respondTo: PaketID, message: String) : Paket() {
    override val id = PaketID.Error

    val code by enum(code)
    val respondTo by enum(respondTo)
    val message by string(message)

    enum class ErrorCodes {
        Success, Auth, Unknown, AccessDenied, AlreadyInState, NotExists, AlreadyExists
    }

    companion object : PaketCreator<ErrorPaket> {
        override fun produce() = ErrorPaket(ErrorCodes.Success, PaketID.NoOp, "")
        fun success(respondTo: PaketID) = ErrorPaket(ErrorCodes.Success, respondTo, "success")
    }
}
