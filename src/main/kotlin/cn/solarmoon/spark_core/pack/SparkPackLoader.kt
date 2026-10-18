package cn.solarmoon.spark_core.pack

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException
import cn.solarmoon.spark_core.pack.diagnostic.PackFrame
import cn.solarmoon.spark_core.pack.diagnostic.PackIssue
import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import cn.solarmoon.spark_core.pack.diagnostic.PackSeverity
import cn.solarmoon.spark_core.pack.diagnostic.PackSide
import cn.solarmoon.spark_core.pack.diagnostic.RuntimeIssueLog
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.diagnostic.event.PackAllFinishedEvent
import cn.solarmoon.spark_core.pack.diagnostic.event.PackModuleFinishEvent
import cn.solarmoon.spark_core.pack.diagnostic.event.PackModuleStartEvent
import cn.solarmoon.spark_core.pack.graph.SparkPackGraph
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import cn.solarmoon.spark_core.pack.readable.ReadableDirectory
import cn.solarmoon.spark_core.pack.readable.ReadableZip
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.Event
import net.neoforged.fml.ModLoader
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object SparkPackLoader {

    val LOGGER = SparkCore.logger("拓展包加载器")

    const val MODULE_NAME = "spark_modules"
    const val META_PATH = "meta.json"

    val graph = SparkPackGraph()
    val readablePathTypes = listOf(ReadableZip, ReadableDirectory)

    lateinit var modules: Map<String, SparkPackModule>
        private set

    /** 本次上下文中 `onStart` 失败的模块：跳过 `read` 与 `onFinish`。 */
    private val failedStart = ConcurrentHashMap<PackLoadContext, MutableSet<String>>()

    /** 本次上下文中模块是否全部成功（`onStart` / `read` / `onFinish`）。 */
    private val moduleSuccess = ConcurrentHashMap<PackLoadContext, ConcurrentHashMap<String, Boolean>>()

    fun initialize(isClientSide: Boolean) {
        val readers = linkedMapOf<String, SparkPackModule>()
        ModLoader.postEvent(SparkPackageReaderRegisterEvent(readers))
        modules = readers
        modules.values.forEach { it.onInitialize(isClientSide) }
        LOGGER.info("已注册 ${modules.size} 个拓展包模块: ${modules.keys}")
    }

    /**
     * 开始一次新的加载会话：只清空该上下文的诊断状态，不碰 [graph] / [modules]。
     */
    fun beginSession(context: PackLoadContext) {
        SparkPackIssues.begin(context)
        failedStart.remove(context)
        moduleSuccess.remove(context)
    }

    /**
     * 读取本地目录下的所有可用包，并将包数据存入资源图
     */
    fun readPackageGraph(context: PackLoadContext) {
        reset()

        val isClientSide = context.isClientSide
        val sparkModulesDir = FMLPaths.GAMEDIR.get().resolve(MODULE_NAME)
        Files.createDirectories(sparkModulesDir)

        Files.list(sparkModulesDir).use { paths ->
            paths.forEach { path ->
                val name = path.fileName.toString()
                // .docs 等隐藏目录不是内容包
                if (name.startsWith(".")) return@forEach

                val type = readablePathTypes.firstOrNull { it.match(path) } ?: return@forEach
                // 目录里没有 meta.json 的只是普通目录，不要当成坏包
                if (type === ReadableDirectory && !Files.isRegularFile(path.resolve(META_PATH))) {
                    return@forEach
                }

                try {
                    val pack = type.readAsPackage(path, isClientSide)
                    graph.addNode(pack)
                    LOGGER.info(
                        "成功读取拓展包 ${pack.meta.id}, 命名空间: ${pack.namespaces}"
                    )
                } catch (e: Exception) {
                    SparkPackIssues.report(
                        PackIssue(
                            moduleId = "graph",
                            packId = null,
                            location = name,
                            severity = PackSeverity.ERROR,
                            message = messageOf(e),
                            cause = e,
                            context = context,
                        )
                    )
                }
            }
        }
    }

    /**
     * 读取当前资源图内的包的具体数据
     */
    fun readPackageContent(context: PackLoadContext) {
        val orderedPacks = try {
            graph.resolveLoadOrder()
        } catch (e: Exception) {
            SparkPackIssues.report(
                PackIssue("graph", null, "-", PackSeverity.ERROR, messageOf(e), e, context)
            )
            // 依赖排序失败：没有模块执行过 onStart，因此也不能让 onFinish 被调用
            modules.keys.forEach { markFailed(context, it) }
            return
        }

        // 按模块注册顺序调用，避免模块依赖问题
        modules.values.forEach moduleLoop@ { module ->
            val moduleFrame = PackFrame(module.id, null, "*", context)
            var started = false
            SparkPackIssues.withFrame(moduleFrame) {
                postEvent(PackModuleStartEvent(context, module.id))
                try {
                    module.onStart(context.isClientSide, context.fromServer)
                    started = true
                } catch (e: Exception) {
                    markFailed(context, module.id)
                    SparkPackIssues.report(moduleFrame.issue(PackSeverity.ERROR, messageOf(e), e))
                }
            }
            if (!started) return@moduleLoop

            var success = true
            orderedPacks.forEach { pack ->
                pack.entries.forEach { (namespace, moduleMap) ->
                    val files = moduleMap[module.id] ?: return@forEach

                    for ((path, content) in files) {
                        val parts = path.split("/")
                        val fileName = parts.last()
                        val pathSegments =
                            if (parts.size > 1) parts.dropLast(1) else emptyList()

                        val fileFrame = PackFrame(
                            module.id,
                            pack.meta.id.toString(),
                            "$namespace:${module.id}/$path",
                            context,
                        )
                        SparkPackIssues.withFrame(fileFrame) {
                            try {
                                module.read(
                                    namespace,
                                    pathSegments,
                                    fileName,
                                    content,
                                    pack,
                                    context.isClientSide,
                                    context.fromServer,
                                )
                            } catch (e: PackContentException) {
                                success = false
                                SparkPackIssues.report(
                                    fileFrame.issue(PackSeverity.ERROR, e.userMessage, e.cause)
                                )
                            } catch (e: Exception) {
                                success = false
                                SparkPackIssues.report(
                                    fileFrame.issue(PackSeverity.ERROR, messageOf(e), e)
                                )
                            }
                        }
                    }
                }
            }
            moduleSuccess.getOrPut(context) { ConcurrentHashMap() }[module.id] = success
        }

        orderedPacks.forEach {
            LOGGER.info("拓展包 ${it.meta.id} 已读取完毕")
        }
    }

    fun injectPackageContent(context: PackLoadContext) {
        modules.values.forEach { module ->
            val frame = PackFrame(module.id, null, "*", context)
            val skipped = failedStart[context]?.contains(module.id) == true
            var success = !skipped && (moduleSuccess[context]?.get(module.id) ?: true)

            if (!skipped) {
                SparkPackIssues.withFrame(frame) {
                    try {
                        module.onFinish(context.isClientSide, context.fromServer)
                    } catch (e: Exception) {
                        success = false
                        SparkPackIssues.report(frame.issue(PackSeverity.ERROR, messageOf(e), e))
                    }
                }
            }

            postEvent(PackModuleFinishEvent(context, module.id, success))
        }

        failedStart.remove(context)
        moduleSuccess.remove(context)

        // P2：汇总/摘要之前发布，订阅者可以在这里补报问题
        postEvent(PackAllFinishedEvent(context))

        if (context.side == PackSide.SERVER) SparkPackIssues.logSummary(context)
    }

    fun getModule(id: String) = modules[id]

    fun reset() {
        graph.originNodes.clear()
    }

    fun collectRemote(): List<SparkPackage> {
        val results = mutableListOf<SparkPackage>()
        graph.originNodes.values.forEach { oPack ->
            val filtered = oPack.entries.mapValues { (_, modules) ->
                modules.filter { getModule(it.key)?.mode?.shouldSend() == true }
                    .toMutableMap()
            }.toMutableMap()

            results.add(SparkPackage(oPack.meta, filtered))
        }
        return results.toList()
    }

    /**
     * 接收服务端发来的拓展包数据，覆盖本地已有的，新增本地没有的（以服务端为准）
     */
    fun acceptRemote(packs: List<SparkPackage>) {
        packs.forEach { newPack ->
            val id = newPack.meta.id
            val existing = graph.originNodes[id]
            if (existing != null) {
                existing.entries.putAll(newPack.entries)
            } else {
                graph.originNodes[id] = newPack
            }
        }
    }

    private fun markFailed(context: PackLoadContext, moduleId: String) {
        failedStart.getOrPut(context) { ConcurrentHashMap.newKeySet() }.add(moduleId)
        moduleSuccess.getOrPut(context) { ConcurrentHashMap() }[moduleId] = false
    }

    private fun postEvent(event: Event) {
        try {
            NeoForge.EVENT_BUS.post(event)
        } catch (e: Exception) {
            RuntimeIssueLog.error(
                Component.literal("内容包加载事件处理失败: ${event.javaClass.simpleName}"),
                e,
            )
        }
    }

    private fun messageOf(e: Throwable): Component =
        (e as? PackContentException)?.userMessage ?: Component.literal(e.message ?: e.toString())

}
