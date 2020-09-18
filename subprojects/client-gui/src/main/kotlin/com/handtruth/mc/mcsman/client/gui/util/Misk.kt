package com.handtruth.mc.mcsman.client.gui.util

import javafx.scene.Node

inline fun <reified R> disabled(node: Node, block: () -> R): R {
    node.isDisable = true
    try {
        return block()
    } finally {
        node.isDisable = false
    }
}
