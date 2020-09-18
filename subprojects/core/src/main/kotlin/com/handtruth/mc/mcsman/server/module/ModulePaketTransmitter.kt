package com.handtruth.mc.mcsman.server.module

import com.handtruth.mc.mcsman.server.session.PaketSession
import com.handtruth.mc.paket.PaketTransmitter

interface ModulePaketTransmitter : PaketTransmitter {
    val session: PaketSession
    val module: Module
}
