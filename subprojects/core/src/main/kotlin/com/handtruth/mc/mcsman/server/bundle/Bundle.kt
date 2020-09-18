package com.handtruth.mc.mcsman.server.bundle

import kotlinx.atomicfu.atomic

class Bundle(
    val group: String,
    val artifact: String,
    version: String,
    val classLoader: ClassLoader,
    val dependencies: List<Bundle>
) {
    val version = Version.parse(version)

    val id = ids.getAndIncrement()

    override fun toString() = "$group:$artifact:$version"

    private companion object {
        private val ids = atomic(1)
    }
}
