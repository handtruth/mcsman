package com.handtruth.mc.mcsman.server.bundle

import com.handtruth.mc.mcsman.server.util.compose
import org.koin.core.KoinComponent
import java.net.URL
import java.util.*

internal class BundleClassLoader : ClassLoader(null), KoinComponent {

    var bundle: Bundle? = null

    private val mainClassLoader = BundleClassLoader::class.java.classLoader

    override fun findClass(name: String): Class<*> {
        val bundle = bundle
        if (bundle != null) for (dependency in bundle.dependencies) {
            try {
                return dependency.classLoader.loadClass(name)
            } catch (e: ClassNotFoundException) {
                // normal
            }
        }
        return mainClassLoader.loadClass(name)
    }

    override fun findResource(name: String): URL? {
        val bundle = bundle ?: return mainClassLoader.getResource(name)
        for (dependency in bundle.dependencies) {
            return dependency.classLoader.getResource(name) ?: continue
        }
        return mainClassLoader.getResource(name)
    }

    override fun findResources(name: String): Enumeration<URL> {
        val bundle = bundle ?: return mainClassLoader.getResources(name)
        val list = mutableListOf<Enumeration<URL>>(mainClassLoader.getResources(name))
        bundle.dependencies.mapNotNullTo(list) {
            val res = it.classLoader.getResources(name)!!
            if (res.hasMoreElements()) res else null
        }
        return compose(list)
    }
}
