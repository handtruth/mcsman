package com.handtruth.mc.mcsman.server.docker

object Labels {
    private const val ns = "com.handtruth.mc.mcsman"
    const val type = "$ns.type"
    const val name = "$ns.name"
    const val network = "$ns.net"
    const val server = "$ns.srv"
    const val game = "$ns.game"
    const val service = "$ns.service"

    object Meta {
        private const val ns = "${Labels.ns}.meta"
        const val stop = "$ns.stop"
    }

    object Services {
        const val companion = "companion"
        const val global = "global"
        const val replica = "replica"
    }

    object Types {
        const val mcsman = "mcsman"
        const val server = "server"
        const val service = "service"
        const val network = "network"
        const val volume = "volume"
    }
}
