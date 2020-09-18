package com.handtruth.mc.mcsman.server.bundle

import com.handtruth.kommon.Log
import com.handtruth.kommon.info
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.event.EventInfo
import com.handtruth.mc.mcsman.common.module.Artifact
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.server.Definitions
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.module.Module
import com.handtruth.mc.mcsman.server.module.Modules
import com.handtruth.mc.mcsman.server.service.Service
import com.handtruth.mc.mcsman.server.service.Services
import com.handtruth.mc.mcsman.server.util.Loggable
import com.handtruth.mc.mcsman.server.util.testL
import com.handtruth.mc.mcsman.server.util.unreachable
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.koin.core.KoinComponent
import org.koin.core.get
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.net.URLClassLoader
import java.util.zip.ZipFile
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

const val modulesDir = "/modules"
const val moduleXmlName = "mcsman.xml"

internal val xmlMapper = XML {
    indent = 1
    indentString = "\t"
    repairNamespaces = true
    xmlDeclMode = XmlDeclMode.Minimal
}

const val manifestNamespace = "http://mcsman.mc.handtruth.com/manifest"

@Serializable
@XmlSerialName("manifest", manifestNamespace, "mcs")
@PublishedApi
internal data class ManifestData(
    val group: String? = null,
    val artifact: String? = null,
    val version: String? = null,
    val mcsmanVersion: String? = null,
    @XmlElement(true)
    @XmlChildrenName("dependency", manifestNamespace, "mcs")
    val dependencies: List<Dependency> = emptyList(),
    @XmlSerialName("module", manifestNamespace, "mcs")
    val module: List<ModuleManifest> = emptyList(),
    @XmlSerialName("service", manifestNamespace, "mcs")
    val service: List<ServiceManifest>,
    @XmlSerialName("event", manifestNamespace, "mcs")
    val event: List<EventManifest>
) {
    @Serializable
    data class ModuleManifest(
        val `class`: String? = null,
        @XmlElement(true)
        @XmlChildrenName("item", manifestNamespace, "mcs")
        val after: List<String> = emptyList(),
        @XmlElement(true)
        @XmlChildrenName("item", manifestNamespace, "mcs")
        val before: List<String> = emptyList(),
        @XmlSerialName("artifact", manifestNamespace, "mcs")
        val artifact: List<ArtifactManifest>
    ) {
        @Serializable
        data class ArtifactManifest(
            val `class`: String,
            val type: String,
            val platform: String,
            val uri: String
        )
    }

    @Serializable
    data class Dependency(
        val group: String,
        val artifact: String,
        val version: String? = null,
        val require: Boolean = true
    )

    @Serializable
    data class ServiceManifest(
        val `class`: String
    )

    @Serializable
    data class EventManifest(
        val `class`: String
    )
}

@PublishedApi
internal data class IntermediateBundleInfo(
    val bundle: Bundle,
    val manifest: ManifestData
) {
    val group get() = bundle.group
    val artifact get() = bundle.artifact
    val version get() = bundle.version
    val dependencies get() = bundle.dependencies.joinToString { it.artifact }
}

@PublishedApi
internal abstract class IntermediateInfo(
    val bundle: Bundle
) {
    val source get() = with(bundle) { "$group:$artifact" }
}

@PublishedApi
internal class IntermediateModuleInfo(
    val module: Module,
    val after: MutableSet<String>,
    val before: Set<String>,
    bundle: Bundle
) : IntermediateInfo(bundle) {
    val name get() = module.name
    val `class` get() = module::class
}

@PublishedApi
internal class IntermediateServiceInfo(
    val service: KClass<out Service>,
    bundle: Bundle
) : IntermediateInfo(bundle)

@PublishedApi
internal class IntermediateEventInfo(
    val event: KClass<out Event>,
    bundle: Bundle
) : IntermediateInfo(bundle)

class Bundles : KoinComponent, Loggable, CoroutineScope {
    override val coroutineContext = MCSManCore.fork("bundle", supervised = false)
    override val log = coroutineContext[Log]!!

    private val bundlesByClassLoader: MutableMap<ClassLoader, Bundle> = hashMapOf()
    private val bundlesById: MutableList<Bundle> = arrayListOf()

    companion object {
        private fun readManifest(string: String): ManifestData {
            val manifest = xmlMapper.parse(ManifestData.serializer(), string)
            val module = manifest.module.map { module ->
                module.copy(after = module.after.map { it.trim() }, before = module.before.map { it.trim() })
            }
            return manifest.copy(module = module)
        }

        fun mergeManifests(a: String, b: String): String {
            val manifestA = readManifest(a)
            val manifestB = readManifest(b)
            val modules = manifestA.module.toMutableList()
            manifestB.module.forEach { module ->
                val index = modules.indexOfFirst { it.`class` == module.`class` || module.`class`.isNullOrEmpty() }
                if (index == -1) {
                    modules += module
                } else {
                    val old = modules[index]
                    modules[index] = old.copy(
                        after = old.after + module.after,
                        before = old.before + module.before,
                        artifact = old.artifact + module.artifact
                    )
                }
            }
            val services = manifestA.service.toMutableList()
            manifestB.service.forEach { service ->
                services.find { it.`class` == service.`class` } ?: run { services += service }
            }
            val events = manifestA.event.toMutableList()
            manifestB.event.forEach { event ->
                events.find { it.`class` == event.`class` } ?: run { events += event }
            }
            val manifest = manifestA.copy(
                dependencies = manifestA.dependencies + manifestB.dependencies,
                module = modules,
                service = services,
                event = events
            )
            return xmlMapper.stringify(ManifestData.serializer(), manifest)
        }
    }

    internal fun findBundles() {
        log.info { "loading bundles..." }
        val loadedBundles = mutableListOf<IntermediateBundleInfo>()
        // load bundles from bundle directory
        for (file in File(modulesDir).walkTopDown()) {
            val classLoader: ClassLoader by lazy {
                URLClassLoader(arrayOf(file.toURI().toURL()), BundleClassLoader())
            }
            try {
                if (file.isDirectory) {
                    val xml = File(file, "META-INF${File.separator}${moduleXmlName}")
                    if (xml.exists()) {
                        val manifest = xmlMapper.parse(ManifestData.serializer(), xml.readText())
                        loadedBundles += loadBundle(manifest, classLoader)
                    } else {
                        log.verbose {
                            "directory \"$file\" ignored due missing manifest file: " +
                                    "\"$file${File.separator}META-INF${File.separator}${moduleXmlName}\""
                        }
                    }
                }
                if (file.isFile && (file.name.endsWith(".zip") || file.name.endsWith(".jar"))) {
                    ZipFile(file).use { zip ->
                        val entry = zip.getEntry("META-INF/${moduleXmlName}")
                        if (entry != null && !entry.isDirectory) {
                            val text = zip.getInputStream(entry).reader().use { it.readText() }
                            val manifest = xmlMapper.parse(ManifestData.serializer(), text)
                            loadedBundles += loadBundle(manifest, classLoader)
                        } else {
                            log.verbose {
                                "archive \"$file\" ignored due missing manifest file: " +
                                        "META-INF${File.separator}${moduleXmlName}\""
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.fatal(e) { "failed to read module's (${file.name}) manifest file" }
            }
        }
        // load from this
        run {
            val loader = javaClass.classLoader
            val xml = loader.getResourceAsStream("META-INF/${moduleXmlName}") ?: return@run
            val text = xml.reader().use { it.readText() }
            val manifest = xmlMapper.parse(ManifestData.serializer(), text)
            loadedBundles += IntermediateBundleInfo(
                Bundle(Definitions.group, Definitions.name, Definitions.version, loader, mutableListOf()), manifest
            )
        }
        // resolve ClassLoader dependencies
        linkBundles(loadedBundles)
        loadedBundles.associateTo(bundlesByClassLoader) { it.bundle.classLoader to it.bundle }
        loadedBundles.mapTo(bundlesById) { it.bundle }
        bundlesById.sortBy { it.id }
        // show bundles
        log.info(
            loadedBundles,
            IntermediateBundleInfo::group, IntermediateBundleInfo::artifact,
            IntermediateBundleInfo::version, IntermediateBundleInfo::dependencies
        ) { "found ${loadedBundles.size} bundles" }
        // load modules
        val loadedModules = mutableListOf<IntermediateModuleInfo>()
        for (bundleInfo in loadedBundles) {
            loadedModules += loadModules(bundleInfo.manifest.module, bundleInfo.bundle)
        }
        get<Modules>().register(loadedModules)
        // show modules
        log.info(
            loadedModules,
            IntermediateModuleInfo::name, IntermediateModuleInfo::`class`,
            IntermediateModuleInfo::after, IntermediateModuleInfo::before,
            IntermediateModuleInfo::source
        ) { "found ${loadedModules.size} modules:" }
        for (loadedModule in loadedModules) {
            val artifacts = loadedModule.module.artifacts
            if (artifacts.isNotEmpty())
                log.info(artifacts) { "auxiliary artifacts of \"${loadedModule.name}\" module" }
        }
        log.info { "all the bundles are loaded" }
        // load services
        val loadedServices = mutableListOf<IntermediateServiceInfo>()
        for (bundleInfo in loadedBundles) {
            loadedServices += loadServices(bundleInfo.manifest.service, bundleInfo.bundle)
        }
        get<Services>().let { services ->
            for (loadedService in loadedServices) {
                services.register(loadedService.service)
            }
        }
        // show services
        log.info(
            loadedServices,
            IntermediateServiceInfo::service, IntermediateServiceInfo::source
        ) { "found ${loadedServices.size} services" }
        // load events
        val loadedEvents = mutableListOf<IntermediateEventInfo>()
        for (bundleInfo in loadedBundles) {
            loadedEvents += loadEvents(bundleInfo.manifest.event, bundleInfo.bundle)
        }
        get<Events>().let { events ->
            for (loadedEvent in loadedEvents) {
                events.register(loadedEvent.event)
            }
        }
        // show events
        log.info(
            loadedEvents,
            IntermediateEventInfo::event, IntermediateEventInfo::source
        ) { "found ${loadedEvents.size} events (declared in manifest)" }
    }

    private fun dependenciesSet(list: List<String>): MutableSet<String> {
        val set = mutableSetOf<String>()
        list.mapTo(set) { it.trim() }
        return set
    }

    private fun loadBundle(manifest: ManifestData, classLoader: ClassLoader): IntermediateBundleInfo {
        if (manifest.mcsmanVersion != null) {
            testL(Version.parse(Definitions.version) >= Version.parse(manifest.mcsmanVersion)) {
                "bundle ${manifest.group}:${manifest.artifact} is too old for current MCSMan core"
            }
        }
        val bundle = Bundle(
            manifest.group ?: "(unknown)", manifest.artifact ?: "(unknown)",
            manifest.version ?: "(unknown)", classLoader, mutableListOf()
        )
        val loader = bundle.classLoader.parent as BundleClassLoader
        loader.bundle = bundle
        return IntermediateBundleInfo(bundle, manifest)
    }

    private fun linkBundles(loadedBundles: List<IntermediateBundleInfo>) {
        loadedBundles.forEach { bundle ->
            val list = bundle.bundle.dependencies as MutableList<Bundle>
            bundle.manifest.dependencies.forEach { dependency ->
                val other = loadedBundles.find { other ->
                    other.bundle.let {
                        it.group == dependency.group && it.artifact == dependency.artifact
                    }
                }
                if (other == null) {
                    if (dependency.require) error(
                        "dependency bundle not found: " +
                                "${dependency.group}:${dependency.artifact}:${dependency.version}"
                    )
                } else {
                    val versionString = dependency.version
                    if (versionString != null) {
                        val version = Version.parse(versionString)
                        if (version > other.version) error(
                            "loaded bundle version is too low (required at least ${version}, got ${other.version})"
                        )
                    }
                    list += other.bundle
                }
            }
        }
    }

    private fun loadModules(
        modulesData: List<ManifestData.ModuleManifest>,
        bundle: Bundle
    ): List<IntermediateModuleInfo> {
        return modulesData.map { moduleData ->
            testL(moduleData.`class` != null) { "module class was not specified" }
            val module = try {
                bundle.classLoader.loadClass(moduleData.`class`).kotlin.objectInstance
            } catch (e: ClassNotFoundException) {
                log.fatal(e) { "failed to load module's main class: ${moduleData.`class`}" }
            } catch (e: NoSuchFieldException) {
                log.fatal(e) { "module (${moduleData.`class`}) is not a Kotlin object" }
            } catch (e: Exception) {
                log.fatal(e) { "unknown error while loading module: ${moduleData.`class`}" }
            }
            testL(module is Module) { "module class (${moduleData.`class`}) is not a subtype of ${Module::class}" }
            module.artifacts = loadArtifacts(moduleData.artifact)
            IntermediateModuleInfo(
                module, dependenciesSet(moduleData.after), dependenciesSet(moduleData.before), bundle
            )
        }
    }

    private fun loadArtifacts(
        artifactsData: List<ManifestData.ModuleManifest.ArtifactManifest>
    ): List<Artifact> {
        return artifactsData.map { artifactData ->
            try {
                Artifact(artifactData.type, artifactData.`class`, artifactData.platform, URI(artifactData.uri))
            } catch (e: URISyntaxException) {
                log.fatal(e) { "failed to parse artifact URI" }
            } catch (e: Exception) {
                log.fatal(e) { "unknown error" }
            }
        }
    }

    private fun loadServices(
        servicesData: List<ManifestData.ServiceManifest>,
        bundle: Bundle
    ): List<IntermediateServiceInfo> {
        return servicesData.map { serviceData ->
            val service = try {
                bundle.classLoader.loadClass(serviceData.`class`).kotlin
            } catch (e: ClassNotFoundException) {
                log.fatal(e) { "failed to load service type class: ${serviceData.`class`}" }
            } catch (e: Exception) {
                log.fatal(e) { "unknown error while loading service: ${serviceData.`class`}" }
            }
            testL(!service.isAbstract) { "service type (${service}) is abstract (should be open at least)" }
            testL(service.isSubclassOf(Service::class)) {
                "declared service type (${service}) does not extend ${Service::class} type"
            }
            @Suppress("UNCHECKED_CAST")
            IntermediateServiceInfo(service as KClass<out Service>, bundle)
        }
    }

    private fun loadEvents(
        eventsData: List<ManifestData.EventManifest>,
        bundle: Bundle
    ): List<IntermediateEventInfo> {
        return eventsData.map { eventData ->
            val event = try {
                bundle.classLoader.loadClass(eventData.`class`).kotlin
            } catch (e: ClassNotFoundException) {
                log.fatal(e) { "failed to load event type class: ${eventData.`class`}" }
            } catch (e: Exception) {
                log.fatal(e) { "unknown error while loading event: ${eventData.`class`}" }
            }
            testL(event.isSubclassOf(Event::class)) {
                "declared event type (${event}) does not extend ${Event::class} type"
            }
            @Suppress("UNCHECKED_CAST")
            IntermediateEventInfo(event as KClass<out Event>, bundle)
        }
    }

    fun get(event: EventInfo.Full): Bundle = bundlesByClassLoader[event.`class`.java.classLoader] ?: unreachable
    fun getOrNull(any: Any): Bundle? = bundlesByClassLoader[any::class.java.classLoader]
    fun get(any: Any): Bundle = getOrNull(any) ?: throw NotExistsMCSManException("no bundle for $any")
    fun getOrNull(id: Int): Bundle? = bundlesById.getOrNull(id - 1)
    fun get(id: Int): Bundle = getOrNull(id) ?: throw NotExistsMCSManException("no bundle with id #$id")
    fun getOrNull(group: String, artifact: String): Bundle? =
        bundlesById.find { it.group == group && it.artifact == artifact }

    fun get(group: String, artifact: String): Bundle =
        getOrNull(group, artifact) ?: throw NotExistsMCSManException("no bundle: $group:$artifact")

    val all: List<Bundle> get() = bundlesById
}
