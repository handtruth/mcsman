package com.handtruth.mc.mcsman.protocol.mcsman

import com.handtruth.mc.paket.PaketPeeking
import com.handtruth.mc.paket.PaketTransmitter

enum class MCSManPaketID {
    GetPKey, ChangePassword,
}

inline val PaketPeeking.id: MCSManPaketID get() = enumValues<MCSManPaketID>()[idOrdinal]
suspend inline fun PaketTransmitter.catch(): MCSManPaketID = enumValues<MCSManPaketID>()[catchOrdinal()]
