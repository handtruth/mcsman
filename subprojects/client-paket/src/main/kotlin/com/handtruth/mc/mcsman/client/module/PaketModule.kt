package com.handtruth.mc.mcsman.client.module

import com.handtruth.kommon.concurrent.Later
import com.handtruth.kommon.concurrent.later
import com.handtruth.mc.mcsman.common.module.Artifact
import com.handtruth.mc.mcsman.protocol.ModulePaket
import com.handtruth.mc.paket.peek
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import java.net.URI

class PaketModule internal constructor(
    override val controller: PaketModules,
    override val id: Int
) : Module {
    private val info = later {
        val paket = controller.client.request(ModulePaket.GetRequest(id)) { peek(ModulePaket.GetResponse) }
        ModuleInfo(
            paket.module,
            paket.name,
            controller.client.bundles.get(paket.bundle),
            paket.enabled,
            paket.depends.map { controller.get(it) }
        )
    }

    @Volatile
    internal var internalName: String? = null

    override val name = later {
        val name = internalName ?: info.get().name
        internalName = name
        name
    }

    override suspend fun inspect() = info.get()

    override val connection: Later<ModuleConnection> = later {
        val ts = controller.tss.await()[id]
        val name = name.get()
        val context = controller.client.coroutineContext
        val context1 = context + Job(context[Job]!!) +
                CoroutineName(context[CoroutineName]!!.name + "/module/" + name)
        val mts = ModulePaketTransmitterImpl(ts, this, context1)
        controller.specifications[name]?.invokeOnConnection(mts) ?: UnknownModuleConnection
    }

    override suspend fun artifacts(type: String?, `class`: String?, platform: String?): List<Artifact> {
        val paket = controller.client.request(
            ModulePaket.ArtifactsRequest(id, type.orEmpty(), `class`.orEmpty(), platform.orEmpty())
        ) {
            peek(ModulePaket.ArtifactsResponse)
        }
        val types = paket.artifactTypes
        val classes = paket.classes
        val platforms = paket.platforms
        val uris = paket.uris
        return List(types.size) { Artifact(types[it], classes[it], platforms[it], URI(uris[it])) }
    }
}
