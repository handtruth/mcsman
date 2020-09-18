package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.string
import com.handtruth.mc.paket.fields.uint16
import com.handtruth.mc.paket.fields.varInt

class HandshakePaket(address: String = "localhost", port: UShort = 1337u) : Paket() {
    override val id = PaketID.Handshake

    val version by varInt(3)
    var address by string(address)
    var port by uint16(port)

    companion object : PaketCreator<HandshakePaket> {
        override fun produce() = HandshakePaket()
    }
}
