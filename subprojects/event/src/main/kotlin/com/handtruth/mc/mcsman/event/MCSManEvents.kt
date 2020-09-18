package com.handtruth.mc.mcsman.event

import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.event.MCSManEvent
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import kotlinx.serialization.Serializable

@MCSManEvent("actor")
interface ActorEvent : Event {
    val actor: String
}

@MCSManEvent("user")
interface UserEvent : Event {
    val user: String
}

@MCSManEvent("group")
interface GroupEvent : Event {
    val group: String
}

@MCSManEvent("perm_subj")
interface PermissionSubjectEvent : Event {
    val userSubject: String?
    val groupSubject: String?
}

@MCSManEvent("volume")
interface VolumeEvent : Event {
    val volume: String
}

@MCSManEvent("duration")
interface DurationEvent : Event {
    val duration: Long
}

@MCSManEvent("server")
interface ServerEvent : Event {
    val server: String
}

@MCSManEvent("service")
interface ServiceEvent : Event {
    val service: String
}

@MCSManEvent("module")
interface ModuleEvent : Event {
    val module: String
}

@MCSManEvent("session")
interface SessionEvent : Event {
    val session: Int
}

@MCSManEvent("group_member")
data class GroupMemberEvent(
    override val user: String,
    override val group: String,
    override val actor: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : UserEvent, GroupEvent, ActorEvent, DirectEvent

@MCSManEvent("creation_server")
data class ServerCreationEvent(
    val image: String,
    val game: String?,
    override val server: String,
    override val actor: String,
    override val direction: Boolean = true, // create - destroy
    override val success: Boolean = true
) : ServerEvent, ActorEvent, DirectEvent

@MCSManEvent("chdesc_server")
data class ChangeServerDescription(
    override val was: String,
    override val become: String,
    override val server: String,
    override val actor: String,
    override val success: Boolean = true
) : ServerEvent, ActorEvent, MutateEvent

@MCSManEvent("volume_access")
data class VolumeAccessEvent(
    val accessLevel: VolumeAccessLevel,
    override val volume: String,
    override val server: String,
    override val actor: String,
    override val groupSubject: String? = null,
    override val userSubject: String? = null,
    override val direction: Boolean = true, // grant - revoke
    override val success: Boolean = true
) : VolumeEvent, ServerEvent, ActorEvent, PermissionSubjectEvent, DirectEvent

@MCSManEvent("session_life_event")
data class SessionLifeEvent(
    override val session: Int,
    override val user: String,
    override val direction: Boolean = true, // begin - end
    override val success: Boolean = true
) : SessionEvent, UserEvent, DirectEvent

@MCSManEvent("manage_server")
data class ManageServerEvent(
    val action: ExecutableActions,
    override val server: String,
    override val actor: String,
    override val success: Boolean = true
) : ServerEvent, ActorEvent

@MCSManEvent("server_life")
data class ServerLifeEvent(
    val transition: Transitions,
    override val server: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : ServerEvent, DirectEvent

@Serializable
enum class Transitions {
    Starting, Pausing
}

@MCSManEvent("command_server")
data class SendCommand2ServerEvent(
    val command: String,
    override val server: String,
    override val actor: String,
    override val success: Boolean = true
) : ServerEvent, ActorEvent

@MCSManEvent("chver_server")
data class ChangeVersionServerEvent(
    override val was: String,
    override val become: String,
    override val server: String,
    override val actor: String,
    override val success: Boolean = true
) : ServerEvent, ActorEvent, MutateEvent

@MCSManEvent("volume_creation")
data class VolumeCreationEvent(
    override val volume: String,
    override val server: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : VolumeEvent, ServerEvent, DirectEvent

@MCSManEvent("server_perm")
data class ServerPermissionEvent(
    val permission: String,
    val allowed: Boolean,
    override val server: String,
    override val actor: String,
    override val userSubject: String? = null,
    override val groupSubject: String? = null,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : ServerEvent, ActorEvent, PermissionSubjectEvent, DirectEvent

@MCSManEvent("global_perm")
data class GlobalPermissionEvent(
    val permission: String,
    val allowed: Boolean,
    override val actor: String,
    override val userSubject: String? = null,
    override val groupSubject: String? = null,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : ActorEvent, PermissionSubjectEvent, DirectEvent

@MCSManEvent("add_user")
data class UserCreationEvent(
    override val user: String,
    override val actor: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : UserEvent, ActorEvent, DirectEvent

@MCSManEvent("block_user")
data class BlockUserEvent(
    override val user: String,
    override val actor: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : UserEvent, ActorEvent, DirectEvent

@MCSManEvent("chemail_user")
data class ChangeUserEMailEvent(
    override val was: String?,
    override val become: String?,
    override val user: String,
    override val actor: String,
    override val success: Boolean = true
) : UserEvent, ActorEvent, MutateEvent

@MCSManEvent("rename_real_user")
data class ChangeUserRealNameEvent(
    override val was: String,
    override val become: String,
    override val user: String,
    override val actor: String,
    override val success: Boolean = true
) : UserEvent, ActorEvent, MutateEvent

@MCSManEvent("group_creation")
data class GroupCreationEvent(
    override val group: String,
    override val actor: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : GroupEvent, ActorEvent, DirectEvent

@MCSManEvent("chown_group")
data class ChangeOwnerOfGroupEvent(
    override val was: String?,
    override val become: String?,
    override val group: String,
    override val actor: String,
    override val success: Boolean = true
) : GroupEvent, ActorEvent, MutateEvent

@MCSManEvent("rename_real_group")
data class ChangeGroupRealNameEvent(
    override val was: String,
    override val become: String,
    override val group: String,
    override val actor: String,
    override val success: Boolean = true
) : GroupEvent, ActorEvent, MutateEvent

@MCSManEvent("action_service")
data class ManageServiceEvent(
    val action: ExecutableActions,
    override val service: String,
    override val actor: String,
    override val success: Boolean = true
) : ServiceEvent, ActorEvent

@MCSManEvent("service_life")
data class ServiceLifeEvent(
    val transition: Transitions,
    override val server: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : ServerEvent, DirectEvent

@MCSManEvent("service_creation")
data class ServiceCreationEvent(
    val state: ByteArray,
    val className: String,
    override val service: String,
    override val actor: String,
    override val direction: Boolean,
    override val success: Boolean = true
) : ServiceEvent, DirectEvent, ActorEvent {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServiceCreationEvent) return false

        if (!state.contentEquals(other.state)) return false
        if (className != other.className) return false
        if (service != other.service) return false
        if (actor != other.actor) return false
        if (direction != other.direction) return false
        if (success != other.success) return false

        return true
    }

    override fun hashCode(): Int {
        var result = state.contentHashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + service.hashCode()
        result = 31 * result + actor.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + success.hashCode()
        return result
    }

}

@MCSManEvent("command_service")
data class SendCommand2ServiceEvent(
    val command: String,
    override val service: String,
    override val actor: String,
    override val success: Boolean = true
) : ServiceEvent, ActorEvent

@MCSManEvent("image_wildcard")
data class ImageWildcardEvent(
    val wildcard: String,
    val allowed: Boolean,
    override val actor: String,
    override val userSubject: String? = null,
    override val groupSubject: String? = null,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : ActorEvent, PermissionSubjectEvent, DirectEvent

@MCSManEvent("mcsman_started")
data class MCSManLifeEvent(
    override val direction: Boolean = true,
    override val success: Boolean = true
) : Event, DirectEvent

@MCSManEvent("login_method")
data class LoginMethodEvent(
    val method: String,
    val algorithm: String,
    val data: Data,
    val expiryDate: Long?,
    override val user: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : UserEvent, DirectEvent {
    @Serializable
    data class Data(val data: String) {
        override fun toString() = "[DATA EXPUNGED]"
    }
}

@MCSManEvent("block_login_method")
data class BlockLoginMethodEvent(
    val method: String,
    val algorithm: String,
    val data: String,
    override val user: String,
    override val direction: Boolean = true,
    override val success: Boolean = true
) : UserEvent, DirectEvent
