package com.handtruth.mc.mcsman.server.service

import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.server.Config
import com.handtruth.mc.mcsman.server.docker.Ext

@MCSManService
class MCSHub(
    config: Config
) : DockerService.Global(
    ImageName("handtruth/mcshub"),
    Ext.Cmd("--domain=${config.domain}", "--control=25563"),
    Ext.Port.Internal(25563u)
)
