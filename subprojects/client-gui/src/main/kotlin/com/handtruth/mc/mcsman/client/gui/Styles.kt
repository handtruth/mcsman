package com.handtruth.mc.mcsman.client.gui

import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        val errorText by cssclass()
        val dialog by cssclass()
        val cooltext by cssclass()
        val fieldNotice by cssclass()
        val delimiter by cssclass()
        val controllerText by cssclass()
        val terminal by cssclass()
    }

    val controlInnerBackground by cssproperty<Color>("-fx-control-inner-background")

    init {
        errorText {
            textFill = c("red")
        }
        dialog {
            padding = box(15.px)
        }
        fieldNotice {
            fontSize = 12.px
            textFill = c("#909090")
        }
        delimiter {
            fontWeight = FontWeight.BOLD
            fontSize = 14.px
        }
        controllerText {
            fontSize = 20.px
        }
        terminal {
            controlInnerBackground.value = Color.BLACK
            fontFamily = "Consolas"
            val green = c("#00ff00")
            highlightFill = green
            highlightTextFill = Color.BLACK
            textFill = green
        }
    }
}
