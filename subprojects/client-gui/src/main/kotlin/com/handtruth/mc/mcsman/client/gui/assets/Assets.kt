package com.handtruth.mc.mcsman.client.gui.assets

import javafx.scene.image.Image
import java.io.InputStream

object Assets {
    fun stream(name: String): InputStream = javaClass.getResourceAsStream(name)
    fun image(name: String): Image = Image(stream(name))
    val icon by lazy { image("icon.svg") }
}
