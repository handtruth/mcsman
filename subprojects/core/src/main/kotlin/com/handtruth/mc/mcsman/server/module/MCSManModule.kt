package com.handtruth.mc.mcsman.server.module

/**
 * This annotation instructs MCSMan annotation processor to add annotated module to manifest.
 *
 * @property after MCSMan modules names that should be loaded before this module
 * @property before MCSMan modules names that should be loaded after this module
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class MCSManModule(
    val after: Array<String> = ["mcsman"],
    val before: Array<String> = []
) {
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Artifact(val type: String, val className: String, val platform: String, val uri: String)
}
