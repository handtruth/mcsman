package com.handtruth.mc.mcsman.client.gui.model

import com.handtruth.mc.mcsman.client.server.Server
import tornadofx.Scope

class ServerScope(val parent: AppScope, val server: Server) : Scope()
