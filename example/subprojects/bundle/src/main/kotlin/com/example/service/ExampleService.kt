package com.example.service

import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.server.service.MCSManService
import com.handtruth.mc.mcsman.server.service.Service

@MCSManService
class ExampleService : Service() {
    override suspend fun getStatus(): ExecutableStatus {
        TODO("Not yet implemented")
    }
}
