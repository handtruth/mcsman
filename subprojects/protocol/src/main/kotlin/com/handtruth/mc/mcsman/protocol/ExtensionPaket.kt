package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.NestSource
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.paket
import com.handtruth.mc.paket.fields.varInt

sealed class ExtensionPaket(module: Int, type: Types) : Paket() {
    override val id = PaketID.Extension

    var module by varInt(module)
    var type by enum(type)

    enum class Types {
        Operate, Disconnect
    }

    class Header(module: Int) : ExtensionPaket(module, Types.Operate) {
        companion object : PaketCreator<Header> {
            override fun produce() = Header(-1)
        }
    }

    class Disconnect(module: Int) : ExtensionPaket(module, Types.Disconnect) {
        companion object : PaketCreator<Disconnect> {
            override fun produce() = Disconnect(-1)
        }
    }

    class Body(module: Int, body: Paket) : ExtensionPaket(module, Types.Operate) {
        var body by paket(body)
    }

    class Source(val module: Int) : NestSource<ExtensionPaket> {
        override fun head() = Header(module)
        override fun produce(paket: Paket) = Body(module, paket)
    }
}
