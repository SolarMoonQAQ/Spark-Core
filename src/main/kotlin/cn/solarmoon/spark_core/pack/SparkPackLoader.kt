package cn.solarmoon.spark_core.pack

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.pack.graph.SparkPackGraph
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import cn.solarmoon.spark_core.pack.modules.SparkPackModule
import cn.solarmoon.spark_core.pack.readable.ReadableDirectory
import cn.solarmoon.spark_core.pack.readable.ReadableZip
import net.neoforged.fml.ModLoader
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files

object SparkPackLoader {

    val LOGGER = SparkCore.logger("拓展包加载器")

    const val MODULE_NAME = "spark_modules"
    const val META_PATH = "meta.json"

    val graph = SparkPackGraph()
    val readablePathTypes = listOf(ReadableZip, ReadableDirectory)

    lateinit var modules: Map<String, SparkPackModule>
        private set

    fun initialize(isClientSide: Boolean) {
        val readers = linkedMapOf<String, SparkPackModule>()
        ModLoader.postEvent(SparkPackageReaderRegisterEvent(readers))
        modules = readers
        modules.values.forEach { it.onInitialize(isClientSide) }
        LOGGER.info("已注册 ${modules.size} 个拓展包模块: ${modules.keys}")
    }

    /**
     * 读取本地目录下的所有可用包，并将包数据存入资源图
     */
    fun readPackageGraph(isClientSide: Boolean) {
        reset()

        val sparkModulesDir = FMLPaths.GAMEDIR.get().resolve(MODULE_NAME)
        Files.createDirectories(sparkModulesDir)

        Files.list(sparkModulesDir).use { paths ->
            paths.forEach { path ->
                try {
                    readablePathTypes.firstOrNull { it.match(path) }?.let {
                        val pack = it.readAsPackage(path, isClientSide)
                        graph.addNode(pack)
                        LOGGER.info(
                            "成功读取拓展包 ${pack.meta.id}, 命名空间: ${pack.namespaces}"
                        )
                    }
                } catch (e: Exception) {
                    LOGGER.error("拓展包读取失败: $path - ${e.message}")
                }
            }
        }
    }

    /**
     * 读取当前资源图内的包的具体数据
     */
    fun readPackageContent(isClientSide: Boolean, fromServer: Boolean) {
        val orderedPacks = try {
            graph.resolveLoadOrder()
        } catch (e: Exception) {
            LOGGER.error("依赖检查失败: ${e.message}")
            return
        }

        // 按模块注册顺序调用，避免模块依赖问题
        modules.values.forEach { it.onStart(isClientSide, fromServer) }

        modules.forEach { (moduleId, module) ->
            orderedPacks.forEach { pack ->
                pack.entries.forEach { (namespace, moduleMap) ->
                    val files = moduleMap[moduleId] ?: return@forEach

                    for ((path, content) in files) {
                        val parts = path.split("/")
                        val fileName = parts.last()
                        val pathSegments =
                            if (parts.size > 1) parts.dropLast(1) else emptyList()

                        try {
                            module.read(
                                namespace,
                                pathSegments,
                                fileName,
                                content,
                                pack,
                                isClientSide,
                                fromServer
                            )
                        } catch (e: Exception) {
                            LOGGER.error("包 ${pack.meta.id} 读取 ${module.id} 模块失败: $e")
                        }
                    }
                }
            }
        }

        orderedPacks.forEach {
            LOGGER.info("拓展包 ${it.meta.id} 已读取完毕")
        }
    }


    fun injectPackageContent(isClientSide: Boolean, fromServer: Boolean) {
        modules.values.forEach { it.onFinish(isClientSide, fromServer) }
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

}