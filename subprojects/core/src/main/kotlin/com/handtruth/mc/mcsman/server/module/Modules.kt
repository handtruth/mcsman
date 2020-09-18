package com.handtruth.mc.mcsman.server.module

import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.server.Configuration
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.bundle.IntermediateModuleInfo
import com.handtruth.mc.mcsman.server.util.TaskBranch
import com.handtruth.mc.mcsman.server.util.testL
import com.handtruth.mc.mcsman.server.util.waitAndGetCancellation
import kotlinx.coroutines.launch
import org.koin.core.inject
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext

data class ModuleNode(
    val module: Module,
    val after: Set<ModuleNode>
) {
    val id = ids++

    private companion object {
        var ids = 1
    }
}

open class Modules : TaskBranch {

    final override val coroutineContext = MCSManCore.fork("module", LogLevel.Fatal)
    final override val log = coroutineContext[Log]!!

    private val config: Configuration by inject()

    private fun notExists(name: String): Nothing = throw NotExistsMCSManException("module \"$name\" does not exists")

    private val modulesByName: MutableMap<String, ModuleNode> = hashMapOf()
    private val modulesById: MutableList<ModuleNode> = arrayListOf()

    fun describeById(id: Int) = modulesById[id - 1]
    fun describeByNameOrNull(name: String) = modulesByName[name]
    fun describeByName(name: String) = describeByNameOrNull(name) ?: notExists(name)
    fun describeOrNull(module: Module) = describeByNameOrNull(module.name)
    fun describe(module: Module) = describeByName(module.name)
    fun configOf(module: Module) = config.module[module.name] ?: notExists(module.name)

    fun getOrNull(name: String) = describeByNameOrNull(name)?.module
    fun get(name: String) = getOrNull(name) ?: notExists(name)
    fun get(id: Int) = describeById(id - 1).module

    val all = modulesById.asSequence().map { it.module }

    val names: Set<String> get() = modulesByName.keys
    val ids get() = modulesById.indices

    fun count() = modulesById.size

    internal fun register(loadedModules: List<IntermediateModuleInfo>) {
        // flatten dependencies
        val moduleMap = loadedModules.associateBy { it.name }
        testL(moduleMap.size == loadedModules.size) {
            "some modules have identical names. Check table above (info log level required)"
        }
        for (moduleSpecs in loadedModules) {
            for (name in moduleSpecs.before) {
                moduleMap[name]?.let { it.after += moduleSpecs.module.name }
            }
        }
        // build dependency tree
        val stack = hashSetOf<String>()
        for (moduleSpecs in loadedModules)
            buildNode(moduleMap, modulesByName, stack, moduleSpecs.name)
        // setup module collections
        modulesById += modulesByName.values
        modulesById.sortBy { it.id }
    }

    private fun buildNode(
        moduleMap: Map<String, IntermediateModuleInfo>,
        allNodes: MutableMap<String, ModuleNode>,
        stack: MutableSet<String>, name: String
    ): ModuleNode? {
        val nodeReady = allNodes[name]
        if (nodeReady != null)
            return nodeReady
        testL(name !in stack) { "dependency cycle found in modules: $stack" }
        stack.add(name)
        val moduleSpecs = moduleMap[name] ?: return null
        val module = moduleSpecs.module
        val after = moduleSpecs.after.asSequence()
            .mapNotNull { buildNode(moduleMap, allNodes, stack, it) }
            .toSet()
        stack.remove(name)
        val node = ModuleNode(module, after)
        allNodes[name] = node
        return node
    }

    override fun provideContext(name: String): CoroutineContext {
        return fork(name, LogLevel.Fatal)
    }

    private fun dependenciesSet(list: List<String>): MutableSet<String> {
        val set = mutableSetOf<String>()
        list.mapTo(set) { it.trim() }
        return set
    }

    fun initializeModules(direction: Boolean = true): List<Module> {
        val initialized = hashSetOf<String>()
        val order = mutableListOf<Module>()
        for (node in modulesByName.values) {
            initializeNode(order, initialized, node, direction)
        }
        return order
    }

    private fun initializeNode(
        order: MutableList<Module>, initialized: MutableSet<String>, node: ModuleNode, direction: Boolean
    ) {
        val name = node.module.name
        if (name in initialized)
            return
        initialized += name
        if (direction) {
            for (dependency in node.after)
                initializeNode(order, initialized, dependency, direction)
            node.module.invokeInitialize()
        } else {
            node.module.invokeShutdown()
            for (dependency in node.after)
                initializeNode(order, initialized, dependency, direction)
        }
        order += node.module
    }


    init {
        launch {
            waitAndGetCancellation()
            log.info { "shutdown modules..." }
            initializeModules(false)
        }
    }
}
