package com.handtruth.mc.mcsman.server

import com.handtruth.mc.mcsman.MCSManException

class MCSManInitializeException(message: String) : MCSManException(message)
class NotMCSManThreadException : MCSManException("not MCSMan thread")
class WrongPhaseMCSManException(val required: MCSManCore.Phases, val current: MCSManCore.Phases = MCSManCore.phase) :
        MCSManException("$required phase required, but $current phase is running")
