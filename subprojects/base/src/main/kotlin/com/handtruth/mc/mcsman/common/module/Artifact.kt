package com.handtruth.mc.mcsman.common.module

import java.net.URI

data class Artifact(val type: String, val `class`: String, val platform: String, val uri: URI)
