package com.handtruth.mc.mcsman.client.gui.util

import javafx.beans.property.*
import java.util.prefs.Preferences

fun Preferences.set(property: ReadOnlyBooleanProperty) {
    putBoolean(property.name, property.value)
}

fun Preferences.set(property: ReadOnlyIntegerProperty) {
    putInt(property.name, property.value)
}

fun Preferences.set(property: ReadOnlyStringProperty) {
    put(property.name, property.value)
}

fun Preferences.load(property: BooleanProperty) {
    property.value = getBoolean(property.name, property.value)
}

fun Preferences.load(property: IntegerProperty) {
    property.value = getInt(property.name, property.value)
}

fun Preferences.load(property: StringProperty) {
    property.value = get(property.name, property.value)
}

fun Preferences.forget(property: ReadOnlyProperty<*>) {
    remove(property.name)
}
