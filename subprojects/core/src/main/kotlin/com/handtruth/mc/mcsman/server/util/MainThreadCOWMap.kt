package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.mcsman.server.MCSManCore

open class MainThreadCOWMap<K, V>(@Volatile private var data: Map<K, V>) : MutableMap<K, V> {
    constructor() : this(emptyMap())

    override val size get() = data.size

    override fun containsKey(key: K) = data.containsKey(key)

    override fun containsValue(value: V) = data.containsValue(value)

    override fun get(key: K) = data[key]

    override fun isEmpty() = data.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = TODO("Not yet implemented")
    override val keys: MutableSet<K>
        get() = TODO("Not yet implemented")
    override val values: MutableCollection<V>
        get() = TODO("Not yet implemented")

    override fun clear() {
        MCSManCore.checkMCSManThread()
        data = emptyMap()
    }

    override fun put(key: K, value: V): V? {
        MCSManCore.checkMCSManThread()
        val old = data[key]
        data = data + (key to value)
        return old
    }

    override fun putAll(from: Map<out K, V>) {
        MCSManCore.checkMCSManThread()
        data = data + from
    }

    override fun remove(key: K): V? {
        MCSManCore.checkMCSManThread()
        val value = data[key]
        data = data - key
        return value
    }
}
