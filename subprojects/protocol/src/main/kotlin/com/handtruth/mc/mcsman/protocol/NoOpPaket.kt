package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.SinglePaket

object NoOpPaket : SinglePaket<NoOpPaket>() {
    override val id = PaketID.NoOp
}
