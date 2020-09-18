package com.handtruth.mc.mcsman.client.server

import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.mcsman.client.util.NamedEntity
import com.handtruth.mc.mcsman.client.util.NamedEntityInfo
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.util.Executable
import com.handtruth.mc.mcsman.util.Removable

interface Server : NamedEntity, Executable, Removable {
    override val controller: Servers

    override suspend fun inspect(): ServerInfo

    suspend fun setDescription(description: ChatMessage)
    suspend fun upgrade()
}

data class ServerInfo(
    override val id: Int,
    override val name: String,
    val status: ExecutableStatus,
    val imageId: String,
    val image: ImageName,
    val game: String?,
    val description: ChatMessage,
    val volumes: List<Volume>
) : NamedEntityInfo
