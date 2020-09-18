package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.paket.PaketPeeking
import com.handtruth.mc.paket.PaketReceiver

val PaketPeeking.id get() = enumValues<PaketID>()[idOrdinal]
suspend inline fun PaketReceiver.catch() = enumValues<PaketID>()[catchOrdinal()]
