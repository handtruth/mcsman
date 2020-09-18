package com.handtruth.mc.mcsman.client.util

import com.handtruth.kommon.concurrent.Later

interface Entity {
    val controller: Controller

    suspend fun inspect(): EntityInfo

    val id: Int
}

interface NamedEntity : Entity {
    override val controller: NamedController

    override suspend fun inspect(): NamedEntityInfo

    val name: Later<String>
}

interface EntityInfo {
    val id: Int
}

interface NamedEntityInfo : EntityInfo {
    val name: String
}
