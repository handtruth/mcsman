package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.fields.enum
import com.handtruth.mc.paket.fields.string
import com.handtruth.mc.paket.fields.varInt

class StreamPaket(source: Sources, identity: Int, type: Types, line: String) : Paket() {
    override val id = PaketID.Stream

    var source by enum(source)
    var identity by varInt(identity)
    var type by enum(type)
    var line by string(line)

    enum class Sources {
        Server, Service
    }

    enum class Types {
        Input, Output, Errors
    }

    companion object : PaketCreator<StreamPaket> {
        override fun produce() = StreamPaket(Sources.Server, 0, Types.Input, "")
    }
}
