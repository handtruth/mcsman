package com.handtruth.mc.mcsman.server.event

import com.handtruth.kommon.getBean
import com.handtruth.kommon.getBeanOrNull
import com.handtruth.kommon.setBean
import com.handtruth.mc.mcsman.common.event.EventInfo
import org.jetbrains.exposed.sql.ColumnSet

inline var EventInfo.table: EventTableBase
    get() = getBean()
    internal set(value) = setBean(value)

class EventQueries internal constructor(
    val load: ColumnSet
)

inline var EventInfo.queries: EventQueries
    get() = getBean()
    internal set(value) = setBean(value)

inline val EventInfo.reactor: Reactor<*>?
    get() = getBeanOrNull()

inline val EventInfo.corrector: Corrector<*>?
    get() = getBeanOrNull()

inline val EventInfo.veto: Veto<*>?
    get() = getBeanOrNull()
