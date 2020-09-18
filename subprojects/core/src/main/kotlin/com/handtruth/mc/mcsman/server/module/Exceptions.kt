package com.handtruth.mc.mcsman.server.module

import com.handtruth.mc.mcsman.MCSManException

sealed class ModuleException(message: String) : MCSManException(message)
class NotFoundModuleException(name: String) : ModuleException("Module $name not found")
class NotRegisteredModuleException(name: String) :
    ModuleException("Module \"$name\" is not registered yet (it seems like module being used before initialize call)")
