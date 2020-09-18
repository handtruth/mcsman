package com.handtruth.mc.mcsman.server

import java.util.*
import kotlin.reflect.KProperty

internal object Definitions {
    private val props = Properties()
    init {
        val resource = javaClass.classLoader.getResource("META-INF/application.properties")
        props.load(resource!!.openStream()!!)
    }
    private operator fun Properties.getValue(thisRef: Definitions, property: KProperty<*>): String {
        return getProperty(property.name)!!
    }

    val group by props
    val name by props
    val version by props
}
