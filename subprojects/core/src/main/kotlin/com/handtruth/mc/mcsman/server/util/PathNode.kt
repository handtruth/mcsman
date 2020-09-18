package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.paket.util.Path

internal class PathNode(
    val part: String,
    val parent: PathNode? = null
) {
    val children: MutableMap<String, PathNode> = mutableMapOf()

    fun insert(path: Path): PathNode {
        var current = this
        path.segments.forEach {
            val name = if (it.startsWith('/'))
                it.substring(1)
            else
                it
            current = current.next(name)
        }
        return current
    }

    fun next(name: String): PathNode {
        val result = children[name]
        return if (result == null) {
            val node = PathNode(name, this)
            children[name] = node
            node
        } else {
            result
        }
    }
}

internal fun leaves(paths: Iterable<Path>): List<PathNode> {
    val root = PathNode("")
    return paths.map { root.insert(it) }
}

private class PathNodeName(var node: PathNode) {
    var name = node.part
        private set

    fun extend() {
        node = node.parent ?: error("reached root path node, but name remains not unique")
        if (node.part.isNotEmpty())
            name = "${node.part}_$name"
    }
}

fun uniqueNames(paths: Iterable<Path>): List<String> {
    val leaves = leaves(paths).map { PathNodeName(it) }
    do {
        var changed = false
        for ((i, a) in leaves.withIndex()) {
            for (j in (i + 1)..leaves.lastIndex) {
                val b = leaves[j]
                while (a.name == b.name) {
                    a.extend()
                    b.extend()
                    changed = true
                }
            }
        }
    } while (changed)
    return leaves.map { it.name }
}
