package cn.solarmoon.spark_core.resource2

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import cn.solarmoon.spark_core.resource2.graph.SparkPackGraph
import cn.solarmoon.spark_core.resource2.readable.ReadableDirectory
import cn.solarmoon.spark_core.resource2.readable.ReadableZip
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import java.nio.file.Files

object SparkPackLoader {

    const val MODULE_NAME = "spark_modules"
    const val META_NAME = "meta.json"

    val graph = SparkPackGraph()
    val readablePathTypes = listOf(ReadableZip, ReadableDirectory)

    lateinit var moduleReaders: Map<String, SparkPackModule>
        private set

    /**
     * 读取本地目录下的所有可用包，并将包数据存入资源图
     */
    fun readPackageGraph() {
        // 注册/刷新所有将读模块
        reload()

        // 如果目录不存在，创建
        val sparkModulesDir = FMLPaths.GAMEDIR.get().resolve(MODULE_NAME)
        Files.createDirectories(sparkModulesDir)

        // 读取并注册每个包
        Files.list(sparkModulesDir).use { paths ->
            paths.forEach { path ->
                    try {
                        readablePathTypes.firstOrNull { it.match(path) }?.let {
                            graph.addNode(it.readAsPackage(path))
                        }
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("压缩包读取失败: $path - ${e.message}")
                    }
                }
        }
    }

    /**
     * 读取当前资源图内的包的具体数据
     */
    fun readPackageContent() {
        // 验证并排序资源图依赖
        val orderedPacks = try {
            graph.resolveLoadOrder()
        } catch (e: Exception) {
            SparkCore.LOGGER.error("依赖检查失败: ${e.message}")
            return
        }

        // 按顺序加载所有的资源的所有已注册模块
        moduleReaders.forEach { (id, reader) ->
            val prefix = "$id/"
            orderedPacks.forEach { pack ->
                pack.entries
                    .filter { it.key.startsWith(prefix) }
                    .forEach { (path, content) ->
                        val relativePath = path.removePrefix(prefix)
                        val parts = relativePath.split("/")
                        val fileName = parts.last()
                        val pathSegments = if (parts.size > 1) parts.dropLast(1) else emptyList()
                        try {
                            reader.read(pathSegments, fileName, content, pack)
                        } catch (e: Exception) {
                            SparkCore.LOGGER.error("包 ${pack.meta.id} 读取 ${reader.moduleName} 模块失败: $e")
                        }
                    }
                SparkCore.LOGGER.info("已加载拓展包 ${pack.meta.id}")
            }
            reader.onFinish()
        }
    }


    fun reload() {
        val readers = mutableMapOf<String, SparkPackModule>()
        NeoForge.EVENT_BUS.post(SparkPackageReaderRegisterEvent(readers))
        moduleReaders = readers
        graph.originNodes.clear()
    }

}