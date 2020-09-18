package com.handtruth.mc.mcsman.protocol.mcsman

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.PaketCreator
import com.handtruth.mc.paket.PaketSingleton
import com.handtruth.mc.paket.fields.byteArray
import com.handtruth.mc.paket.fields.int64
import com.handtruth.mc.paket.fields.string

sealed class GetPKeyPaket : Paket() {
    final override val id = MCSManPaketID.GetPKey

    object Request : GetPKeyPaket(), PaketSingleton<Request> {
        override fun produce() = this
    }

    class Response(algorithm: String, expireDate: Long, data: ByteArray) : GetPKeyPaket() {
        val algorithm by string(algorithm)
        val expireDate by int64(expireDate)
        val data by byteArray(data)

        companion object : PaketCreator<Response> {
            override fun produce() = Response("", 0L, ByteArray(0))
        }
    }
}
