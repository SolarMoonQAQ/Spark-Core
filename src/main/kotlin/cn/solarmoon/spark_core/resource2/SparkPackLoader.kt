package cn.solarmoon.spark_core.resource2

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.resource2.graph.SparkPackGraph
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.ReadMode
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import cn.solarmoon.spark_core.resource2.readable.ReadableDirectory
import cn.solarmoon.spark_core.resource2.readable.ReadableZip
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
        val readers = mutableMapOf<String, SparkPackModule>()
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
        // 如果目录不存在，创建
        val sparkModulesDir = FMLPaths.GAMEDIR.get().resolve(MODULE_NAME)
        Files.createDirectories(sparkModulesDir)

        // 读取并注册每个包
        Files.list(sparkModulesDir).use { paths ->
            paths.forEach { path ->
                try {
                    readablePathTypes.firstOrNull { it.match(path) }?.let {
                        val pack = it.readAsPackage(path, isClientSide)
                        graph.addNode(pack)
                        LOGGER.info("成功读取拓展包 ${pack.meta.id}, 包含模块: ${pack.modules}")
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
    fun readPackageContent(isClientSide: Boolean) {
        // 验证并排序资源图依赖
        val orderedPacks = try {
            graph.resolveLoadOrder()
        } catch (e: Exception) {
            LOGGER.error("依赖检查失败: ${e.message}")
            return
        }

        // 按顺序加载所有的资源的所有已注册模块
        modules.forEach { it.value.onStart(isClientSide) }
        orderedPacks.forEach { pack ->
            modules.forEach { (id, module) ->
                pack.entries[id]?.forEach { (path, content) ->
                    val parts = path.split("/")
                    val fileName = parts.last()
                    val pathSegments = if (parts.size > 1) parts.dropLast(1) else emptyList()
                    try {
                        module.read(pathSegments, fileName, content, pack, isClientSide)
                    } catch (e: Exception) {
                        LOGGER.error("包 ${pack.meta.id} 读取 ${module.id} 模块失败: $e")
                    }
                }
            }
            LOGGER.info("拓展包 ${pack.meta.id} 已加载完毕")
        }
        modules.forEach { it.value.onFinish(isClientSide) }
    }

    fun getModule(id: String) = modules[id]

    fun reset() {
        graph.originNodes.clear()
    }

    fun collectRemote(): List<SparkPackage> {
        val results = mutableListOf<SparkPackage>()
        graph.originNodes.values.forEach { oPack ->
            results.add(SparkPackage(oPack.meta, oPack.entries.filter { getModule(it.key)?.mode?.shouldSend() == true }.toMutableMap()))
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
                // 已存在 → 用新的 entries 替换
                existing.entries.putAll(newPack.entries)
            } else {
                // 不存在 → 直接放进去
                graph.originNodes[id] = newPack
            }
        }
    }

}