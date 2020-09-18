package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.Styles
import com.handtruth.mc.mcsman.client.gui.util.CoroutineView
import javafx.animation.Timeline
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.VPos
import javafx.util.Duration
import tornadofx.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MCSManView : CoroutineView("main", "MCSMan") {

    private inline val radius get() = 200
    private inline val circleRadius get() = 60
    private inline val controllers get() = controller.controllers
    private inline val count get() = controllers.size
    private inline val circleStrokeWidth get() = 4.0
    private inline val strokeDashOffsetWidth get() = 4.0

    private inline val offset get() = 5.0

    private data class Coordinates(val x: Double, val y: Double)

    override val root = pane {
        val spaceSize = radius + circleRadius + circleStrokeWidth + offset
        prefWidth = spaceSize * 2
        prefWidth = spaceSize * 2
        group {
            val angleStep = 2 * PI / count
            var angle = .0

            val coordinates = mutableListOf<Coordinates>()

            val dashOffset = SimpleDoubleProperty(.0)

            for (i in 0 until count) {
                val y = -radius * cos(angle) + spaceSize
                val x = radius * sin(angle) + spaceSize
                coordinates += Coordinates(x, y)
                angle += angleStep
                for (j in 0 until i) {
                    val second = coordinates[j]
                    line(x, y, second.x, second.y) {
                        strokeDashArray += strokeDashOffsetWidth
                        strokeDashOffsetProperty().bind(dashOffset)
                    }
                }
            }

            timeline {
                keyframe(Duration.seconds(1.0)) {
                    keyvalue(dashOffset, strokeDashOffsetWidth * 2)
                }
                cycleCount = Timeline.INDEFINITE
            }

            for (i in coordinates.indices) {
                val (xPos, yPos) = coordinates[i]
                val controller = controllers[i]
                stackpane {
                    val offset = circleRadius + circleStrokeWidth
                    layoutX = xPos - offset
                    layoutY = yPos - offset
                    val fillColor = c("#d5e8d4")
                    val strokeColor = c("#82b366")

                    val symbol = circle(radius = offset) {
                        fill = fillColor
                        stroke = strokeColor
                        strokeWidth = circleStrokeWidth
                    }
                    text(controller.name) {
                        textOrigin = VPos.CENTER
                        addClass(Styles.controllerText)
                    }
                    val enteredFill = c("#e5f8e4")
                    val enteredStroke = c("#92c376")
                    setOnMouseEntered {
                        symbol.fill = enteredFill
                        symbol.stroke = enteredStroke
                    }
                    setOnMouseExited {
                        symbol.fill = fillColor
                        symbol.stroke = strokeColor
                    }
                    setOnMousePressed {
                        symbol.fill = c("#c5d8c4")
                        symbol.stroke = c("#72a356")
                    }
                    setOnMouseClicked {
                        symbol.fill = enteredFill
                        symbol.stroke = enteredStroke
                        controller.open(this@MCSManView)
                    }
                }
            }

            circle(spaceSize, spaceSize, circleRadius) {
                fill = c("#dae8fc")
                stroke = c("#667d9d")
                strokeWidth = circleStrokeWidth
            }
        }

        controller.activateShortcuts(this@MCSManView)
    }
}
