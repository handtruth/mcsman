@file:Suppress("UNUSED_VARIABLE")

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin

plugins {
    id("com.gladed.androidgitversion")
    kotlin("jvm") apply false
    kotlin("kapt") apply false
    kotlin("plugin.serialization") apply false
    id("com.github.johnrengelman.shadow") apply false
    id("com.jraska.module.graph.assertion") version "1.3.1"
}

moduleGraphAssert {
    configurations = setOf("api", "implementation", "kapt", "providedCompile")
}

androidGitVersion {
    prefix = "v"
}

val mcsmanVersion = androidGitVersion.name()
val mcsmanVersionCode = androidGitVersion.code()

val mcsmanLibs: List<String> by extra {
    listOf(
        "mcspack-gradle",
        "protocol",
        "core",
        "base",
        "compiler-base",
        "compiler-event",
        "compiler-event-table",
        "compiler-bundle",
        "module-minecraft-server",
        "event",
        "event-table-base",
        "client",
        "client-paket",
        "client-gui",
        "test"
    )
}

allprojects {
    group = "com.handtruth.mc"
    version = mcsmanVersion

    repositories {
        maven("https://dl.bintray.com/pdvrieze/maven")
        jcenter()
        maven("https://mvn.handtruth.com")
        mavenLocal()
    }
}

val platformRequirements = listOf(
    "implementation", "runtimeOnly", "providedRuntime", "kapt"
)

val rootDir = projectDir

fun Project.preparePart() {
    apply<KotlinPluginWrapper>()
    apply<Kapt3GradleSubplugin>()
    apply<JacocoPlugin>()
    apply<MavenPublishPlugin>()

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mcsman") {
                from(components["kotlin"])
            }
        }
    }

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.5"
        reportsDir = file("$buildDir/jacoco-reports")
    }

    configure<KotlinJvmProjectExtension> {
        sourceSets.all {
            with(languageSettings) {
                useExperimentalAnnotation("kotlin.RequiresOptIn")
                useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
                useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
                useExperimentalAnnotation("kotlin.time.ExperimentalTime")
                useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
                useExperimentalAnnotation("com.handtruth.mc.paket.ExperimentalPaketApi")
                useExperimentalAnnotation("com.handtruth.mc.mcsman.InternalMCSManApi")
                useExperimentalAnnotation("kotlinx.serialization.ImplicitReflectionSerializer")
                useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
                enableLanguageFeature("InlineClasses")
            }
        }
    }

    val platformType = Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java)

    configurations {
        "kapt" {
            attributes {
                attribute(platformType, "jvm")
            }
        }
    }

    dependencies {
        val platformVersion: String by project
        val handtruthPlatform = platform("com.handtruth.internal:platform:$platformVersion")
        configurations.all {
            if (name in platformRequirements)
                name(handtruthPlatform)
        }
        "api"(kotlin("stdlib-jdk8"))
        "testImplementation"(kotlin("test-junit5"))
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
        withType<JacocoReport> {
            reports {
                xml.isEnabled = false
                csv.isEnabled = false
                html.destination = file("$buildDir/jacoco-html")
            }
        }
    }

    if (name == "mcsman-core")
        apply<ApplicationPlugin>()

    if (name.startsWith("mcsman-module-") && name.endsWith("-server")) {
        apply<Kapt3GradleSubplugin>()
        apply<SerializationGradleSubplugin>()

        val depsFile = File("$buildDir/mcsman-bundle-deps.txt")

        configure<KaptExtension> {
            arguments {
                arg("com.handtruth.mc.mcsman.group", group)
                arg("com.handtruth.mc.mcsman.artifact", this@preparePart.name)
                arg("com.handtruth.mc.mcsman.version", mcsmanVersion)
                arg("com.handtruth.mc.mcsman.versionCode", mcsmanVersionCode)
                arg("com.handtruth.mc.mcsman.deps", depsFile.absolutePath)
            }
        }

        val optionalMcsmanBundle by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
        }
        val mcsmanBundle by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
        }
        val providedCompile by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
            extendsFrom(optionalMcsmanBundle)
            extendsFrom(mcsmanBundle)
        }
        val providedRuntime by configurations.creating {
            attributes {
                attribute(platformType, "jvm")
            }
            extendsFrom(providedCompile)
        }
        val kapt by configurations.getting
        val runtime by configurations.getting {
            extendsFrom(providedRuntime)
        }

        val sourceSets = project.the<SourceSetContainer>()

        sourceSets["main"].compileClasspath += providedCompile

        dependencies {
            providedCompile(project(":mcsman-core"))
            kapt(project(":mcsman-compiler-bundle"))
        }

        val depsFileTask by tasks.creating {
            doLast {
                depsFile.writer().use { writer ->
                    mcsmanBundle.allDependencies.forEach {
                        writer.appendln("${it.group}:${it.name}:${it.version}:true")
                    }
                    optionalMcsmanBundle.allDependencies.forEach {
                        writer.appendln("${it.group}:${it.name}:${it.version}:false")
                    }
                }
            }
        }

        tasks.withType<KaptTask> {
            dependsOn(depsFileTask)
        }

        @Suppress("UNUSED_VALUE")
        val mcsmanBundleJar by tasks.creating(Jar::class) {
            description = "Build MCSMan resource bundle"
            group = "mcsman"

            var baseName by archiveBaseName
            baseName = "$baseName-bundle"

            val classpath by lazy { sourceSets[SourceSet.MAIN_SOURCE_SET_NAME]!!.runtimeClasspath }

            dependsOn(Callable { classpath })
            from(Callable<Iterable<Any>> {
                val packedClasspath = classpath - providedRuntime
                packedClasspath.map { if (it.exists() && !it.isDirectory) zipTree(it) else it }
            })
        }

        val prepareBundle by tasks.creating(Copy::class) {
            group = "mcsman"
            dependsOn(mcsmanBundleJar)
            from(mcsmanBundleJar.archiveFile)
            into("${rootProject.projectDir}/docker/modules")
        }
    }
}

mcsmanLibs.map { project(":mcsman-$it") }.forEach { it.preparePart() }

tasks {
    val bundles = Callable {
        mcsmanLibs.filter { it.startsWith("module-") && it.endsWith("-server") }.map {
            project(":mcsman-$it").tasks.getByName("prepareBundle")
        }
    }

    val prepareInstallDist by creating(Copy::class) {
        group = "mcsman"
        val installDist by project(":mcsman-core").tasks.getting(Sync::class)
        dependsOn(installDist)
        from(installDist.destinationDir).into("docker/app")
    }

    val prepareDockerDebug by creating {
        dependsOn(bundles)
        dependsOn(prepareInstallDist)
    }

    val clean by registering(Delete::class) {
        group = BasePlugin.BUILD_GROUP
        delete("docker/app", "docker/modules")
    }
}
