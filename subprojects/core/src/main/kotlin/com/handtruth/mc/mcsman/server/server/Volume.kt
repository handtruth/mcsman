package com.handtruth.mc.mcsman.server.server

import com.handtruth.mc.mcsman.event.VolumeAccessEvent
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.model.VolumeTable
import com.handtruth.mc.mcsman.server.session.getActorName
import com.handtruth.mc.mcsman.server.util.IntIdShadow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import org.koin.core.KoinComponent
import org.koin.core.get

class Volume : IntIdShadow<Volume>(), KoinComponent {

    override val controller = get<Servers.Volumes>()

    val name by VolumeTable.name
    val server by controller.servers with VolumeTable.server

    @ReactorContext
    override suspend fun onDelete() {
        val actor = getActorName()
        controller.accesses.volume.list(this).buffer().collect {
            controller.events.raise(
                VolumeAccessEvent(
                    it.accessLevel, name, server.get().name, actor, it.user?.name, it.group?.name, false
                )
            )
        }
        super.onDelete()
    }

    override fun toString() = "volume: $name"
}
