package cn.solarmoon.spark_core.resource.handler

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.autoregistry.AutoRegisterHandler
import cn.solarmoon.spark_core.resource.autoregistry.HandlerDiscoveryService
import cn.solarmoon.spark_core.resource.common.ResourceHandlerBase
import cn.solarmoon.spark_core.resource.graph.ModuleGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * 元数据与模块定义处理器
 *
 * 负责在游戏启动时发现、解析和注册所有模块，并构建模块依赖图。
 * 它取代了旧的 ModuleManager 的文件操作职责。
 */
@AutoRegisterHandler
class MetaHandler : ResourceHandlerBase() {

    companion object {
        init {
            HandlerDiscoveryService.registerHandler {
                MetaHandler()
            }
        }
    }

    override fun getSupportedExtensions(): Set<String> = setOf(OModuleInfo.DESCRIPTOR_FILE_NAME, "spark")

    override fun getRegistryIdentifier(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "modules")

    override fun getResourceType(): String = "modules"

    override fun getPriority(): Int = 0 // 最高优先级，必须先于所有其他handler执行

    override fun initialize(modMainClass: Class<*>): Boolean {
        SparkCore.LOGGER.info("开始模块发现和注册...")
        ModuleGraphManager.clear()
        
        val sparkCoreDir = FMLPaths.GAMEDIR.get().resolve("sparkcore")
        val sparkModulesDir = FMLPaths.GAMEDIR.get().resolve("spark_modules")

        // 1. 发现所有模块信息
        if (Files.exists(sparkModulesDir) && Files.isDirectory(sparkModulesDir)) {
            discoverSparkPackages(sparkModulesDir)
        }
        if (Files.exists(sparkCoreDir) && Files.isDirectory(sparkCoreDir)) {
            discoverNamespaceDirectories(sparkCoreDir)
        }
        
        // 2. 构建依赖图
        ModuleGraphManager.buildDependencyGraph()

        SparkCore.LOGGER.info("模块发现和注册完成。")
        return true
    }

    private fun discoverSparkPackages(directory: Path) {
        try {
            Files.walk(directory, 1).use { pathStream ->
                pathStream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".spark") }
                    .forEach { sparkFile ->
                        // 从.spark包文件名提取模块信息
                        val fileName = sparkFile.fileName.toString()
                        val packageName = fileName.substringBeforeLast(".spark")

                        // 对于.spark包，假设格式为 modId_moduleName.spark 或默认为 modId_modId.spark
                        val (modId, moduleName) = if (packageName.contains("_")) {
                            val parts = packageName.split("_", limit = 2)
                            Pair(parts[0], parts[1])
                        } else {
                            // 如果没有下划线，默认为 modId_modId 格式
                            Pair(packageName, packageName)
                        }

                        SparkCore.LOGGER.debug("发现Spark包: $modId:$moduleName (文件: $fileName)")

                        // 注册为资源节点，以保持一致性
                        //ResourceGraphManager.addOrUpdateResource(sparkFile, OModuleInfo.DESCRIPTOR_FILE_NAME)
                        parseAndRegisterModule(sparkFile, modId, moduleName, true)
                    }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描Spark包目录失败: $directory", e)
        }
    }

    private fun discoverNamespaceDirectories(directory: Path) {
        try {
            // 四层目录结构：run/sparkcore/{modId}/{moduleName}/
            Files.list(directory).use { modDirs ->
                modDirs.filter { it.isDirectory() && it.fileName.toString() != "meta" }
                    .forEach { modDir ->
                        val modId = modDir.fileName.toString()
                        SparkCore.LOGGER.debug("扫描mod目录: $modId")

                        // 扫描mod目录下的模块
                        Files.list(modDir).use { moduleDirs ->
                            moduleDirs.filter { it.isDirectory() }
                                .forEach { moduleDir ->
                                    val moduleName = moduleDir.fileName.toString()
                                    SparkCore.LOGGER.debug("发现模块: $modId:$moduleName")

                                    val descriptorFile = moduleDir.resolve(OModuleInfo.DESCRIPTOR_FILE_NAME)
                                    if (descriptorFile.exists()) {
                                        // 注册为资源节点
                                        ResourceGraphManager.addOrUpdateResource(descriptorFile, OModuleInfo.DESCRIPTOR_FILE_NAME)
                                        parseAndRegisterModule(moduleDir, modId, moduleName, false)
                                    }
                                }
                        }
                    }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描四层目录结构失败: $directory", e)
        }
    }

    private fun parseAndRegisterModule(path: Path, modId: String, moduleName: String, isSparkPackage: Boolean) {
        val descriptorContent: String? = if (isSparkPackage) {
            // 从.spark包中读取描述文件
            try {
                ZipFile(path.toFile()).use { zip ->
                    val entry = zip.getEntry(OModuleInfo.DESCRIPTOR_FILE_NAME)
                    zip.getInputStream(entry)?.bufferedReader()?.readText()
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("读取Spark包失败: $path", e)
                null
            }
        } else {
            // 从目录中读取描述文件
            val descriptorFile = path.resolve(OModuleInfo.DESCRIPTOR_FILE_NAME)
            if (descriptorFile.exists() && descriptorFile.isRegularFile()) {
                descriptorFile.readText()
            } else {
                null
            }
        }

        if (descriptorContent != null) {
            try {
                val originalModuleInfo = OModuleInfo.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(descriptorContent)).orThrow.first

                // 为四层目录结构生成正确的模块ID格式：modId:moduleName
                val fullModuleId = "$modId:$moduleName"
                val updatedModuleInfo = originalModuleInfo.copy(id = fullModuleId)

                SparkCore.LOGGER.debug("注册模块: $fullModuleId (原ID: ${originalModuleInfo.id})")
                ModuleGraphManager.registerModule(updatedModuleInfo)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("解析模块描述文件失败: $path", e)
            }
        }
    }

    override fun processResourceAdded(node: ResourceNode) {
        initialize(this.javaClass)
    }

    override fun processResourceModified(node: ResourceNode) {
        initialize(this.javaClass)
    }

    override fun processResourceRemoved(node: ResourceNode) {
        initialize(this.javaClass)
    }

    override fun cleanupModuleResource(resourceLocation: ResourceLocation) {
        // 模块卸载时的清理逻辑
    }
} 