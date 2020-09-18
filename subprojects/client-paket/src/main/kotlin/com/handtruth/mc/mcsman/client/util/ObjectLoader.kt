package com.handtruth.mc.mcsman.client.util

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun <T : Any> loadObjects(`class`: KClass<out T>): List<T> = loadObjects(`class`, `class`.java.classLoader)

fun <T : Any> loadObjects(`class`: KClass<out T>, classLoader: ClassLoader): List<T> {
    val resources = classLoader.getResources("META-INF/services/${`class`.java.canonicalName}")!!
    return resources.asSequence().flatMap { resource ->
        resource.openStream().bufferedReader().useLines { lines ->
            lines.map {
                val objectClass = classLoader.loadClass(it).kotlin
                check(objectClass.isSubclassOf(`class`))
                @Suppress("UNCHECKED_CAST")
                objectClass.objectInstance as T
            }
        }
    }.toList()
}

inline fun <reified T : Any> loadObjects(classLoader: ClassLoader): List<T> = loadObjects(T::class, classLoader)

inline fun <reified T : Any> loadObjects(): List<T> = loadObjects(T::class)
