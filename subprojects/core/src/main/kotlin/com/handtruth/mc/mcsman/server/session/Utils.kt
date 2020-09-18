package com.handtruth.mc.mcsman.server.session

import com.handtruth.kommon.getLog
import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.protocol.ErrorPaket
import com.handtruth.mc.mcsman.protocol.TypedPaket
import com.handtruth.mc.mcsman.protocol.id
import com.handtruth.mc.paket.*

internal suspend fun PaketTransmitter.noResponseHandle(block: suspend (PaketPeeking) -> Unit) {
    try {
        block(this)
    } catch(e: AlreadyInStateMCSManException) {
        send(ErrorPaket(ErrorPaket.ErrorCodes.AlreadyInState, id, e.message.orEmpty()))
    } catch(e: NotExistsMCSManException) {
        send(ErrorPaket(ErrorPaket.ErrorCodes.NotExists, id, e.message.orEmpty()))
    } catch(e: AlreadyExistsMCSManException) {
        send(ErrorPaket(ErrorPaket.ErrorCodes.AlreadyExists, id, e.message.orEmpty()))
    } catch (e: AccessDeniedException) {
        send(ErrorPaket(ErrorPaket.ErrorCodes.AccessDenied, id, e.message.orEmpty()))
    } catch (e: Exception) {
        getLog().error(e)
        send(ErrorPaket(ErrorPaket.ErrorCodes.Unknown, id, e.message.orEmpty()))
    }
}

internal suspend fun PaketTransmitter.handle(block: suspend (PaketPeeking) -> Paket?) {
    noResponseHandle {
        send(block(it) ?: ErrorPaket.success(id))
    }
}

internal suspend fun <E, P> PaketTransmitter.handleTyped(
    source: PaketSource<P>,
    block: suspend PaketPeeking.(E) -> P?
) where E : Enum<E>, P : Paket, P : TypedPaket<E> {
    handle {
        val header = it.peek(source)
        val type = header.type
        getLog().debug { "paket: ${it.id}.$type" }
        it.block(type)
    }
}
