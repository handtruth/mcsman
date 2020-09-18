package com.handtruth.mc.mcsman.server.access

import com.handtruth.mc.mcsman.server.MCSManCore

abstract class Permissions {

    private class PermissionInfo {
        @Volatile var allows: Set<String> = emptySet()
        @Volatile var has: Set<String> = emptySet()
    }

    @Volatile private var all: Map<String, PermissionInfo> = emptyMap()

    private fun infoOf(permission: String): PermissionInfo = all[permission] ?: run {
        val info = PermissionInfo()
        all = all + (permission to info)
        info
    }

    fun allowsAlso(permission: String, others: Collection<String>) {
        MCSManCore.checkInitialization()
        val info = infoOf(permission)
        info.allows += others
        for (other in others)
            infoOf(other).has += permission
    }

    fun allowsAlso(permission: String, vararg others: String) {
        MCSManCore.checkInitialization()
        val info = infoOf(permission)
        info.allows += others
        for (other in others)
            infoOf(other).has += permission
    }

    fun getSuperiors(permission: String): Set<String> = all[permission]?.has ?: emptySet()

    fun getInferiors(permission: String): Set<String> = all[permission]?.allows ?: emptySet()

    class Server : Permissions()
    class Global : Permissions()

}
