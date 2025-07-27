package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.Logger
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 多模块资源提取工具
 * 支持多mod资源提取到统一的sparkcore目录，利用现有的依赖图覆盖机制
 * 保持向后兼容性，所有资源使用统一的spark_core命名空间
 */
object MultiModuleResourceExtractionUtil {

    /**
     * 为指定mod提取资源到sparkcore目录（向后兼容方法）
     * 从modMainClass推断modId
     *
     * @param modMainClass 模组主类
     * @param resourceType 资源类型 (如 "animations", "models", "textures" 等)
     * @return 是否全部提取成功
     */
    fun extractAllModuleResources(
        modMainClass: Class<*>,
        resourceType: String
    ): Boolean {
        val modId = inferModIdFromClass(modMainClass)
        return extractModResourcesForMod(modId, modMainClass, resourceType)
    }

    /**
     * 为指定mod提取资源到sparkcore目录
     * 所有mod的资源都提取到同一个目录，依赖图系统处理覆盖关系
     *
     * @param modId 模组ID
     * @param modMainClass 模组主类
     * @param resourceType 资源类型 (如 "animations", "models", "textures" 等)
     * @param logger 日志记录器
     * @return 是否全部提取成功
     */
    fun extractModResourcesForMod(
        modId: String,
        modMainClass: Class<*>,
        resourceType: String
    ): Boolean {
        val gameDir = FMLPaths.GAMEDIR.get()
        // 多mod多模块架构：所有模块都提取到sparkcore目录下
        val sparkcoreBaseDir = gameDir.resolve("sparkcore")
        var allSuccessful = true

        try {
            var sparkcoreAssetsUrl = findCorrectResourceUrl(modId)
            // 如果新方法失败，fallback到原有的类加载器方式（向后兼容）
            if (sparkcoreAssetsUrl == null) {
                sparkcoreAssetsUrl = modMainClass.classLoader.getResource("assets/sparkcore/")
            } else {
                SparkCore.LOGGER.info("调试 - NeoForge API查找成功: $sparkcoreAssetsUrl")
            }
            if (sparkcoreAssetsUrl == null) {
                return true // 视为成功，因为可能确实没有资源
            }

            val sparkcoreAssetsPath = Paths.get(sparkcoreAssetsUrl.toURI())
            if (!Files.exists(sparkcoreAssetsPath)) {
                return true
            }

            // 遍历assets/sparkcore/下的所有模块目录
            Files.list(sparkcoreAssetsPath).use { moduleStream ->
                moduleStream
                    .filter { Files.isDirectory(it) }
                    .forEach { moduleDir ->
                        val moduleName = moduleDir.fileName.toString()

                        // 跳过特殊目录
                        if (moduleName.startsWith(".") || moduleName.startsWith("_")) {
                            return@forEach
                        }

                        // 查找指定的资源类型目录
                        val resourceTypePath = moduleDir.resolve(resourceType)
                        if (Files.exists(resourceTypePath)) {
                            // 四层目录结构：run/sparkcore/{modId}/{moduleName}/{resourceType}/
                            val targetModDir = sparkcoreBaseDir.resolve(modId)
                            val targetModuleDir = targetModDir.resolve(moduleName)

                            val success = ResourceExtractionUtil.extractResourcesFromJar(
                                modMainClass,
                                "assets/sparkcore/$moduleName/$resourceType",
                                targetModuleDir.toFile(),
                                resourceType,
                                SparkCore.LOGGER
                            )

                            if (success) {
                                SparkCore.LOGGER.info("成功为mod '$modId' 提取模块 '$moduleName' 的 $resourceType 资源到 '$modId/$moduleName'")
                            } else {
                                SparkCore.LOGGER.warn("为mod '$modId' 提取模块 '$moduleName' 的 $resourceType 资源失败")
                                allSuccessful = false
                            }
                        } else {
                            SparkCore.LOGGER.debug("mod '$modId' 的模块 '$moduleName' 没有 $resourceType 资源目录")
                        }
                    }
            }
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("为mod '$modId' 提取多模块 $resourceType 资源时出错", e)
            allSuccessful = false
        }

        return allSuccessful
    }

    
    /**
     * 列出JAR中所有可用的模块
     */
    fun listAvailableModules(modMainClass: Class<*>): List<String> {
        val modules = mutableListOf<String>()
        
        try {
            val sparkCoreUrl = modMainClass.classLoader.getResource("assets/spark_core/")
            if (sparkCoreUrl != null) {
                val sparkCorePath = Paths.get(sparkCoreUrl.toURI())
                if (Files.exists(sparkCorePath)) {
                    Files.list(sparkCorePath).use { moduleStream ->
                        moduleStream
                            .filter { Files.isDirectory(it) }
                            .map { it.fileName.toString() }
                            .filter { !it.startsWith(".") && !it.startsWith("_") }
                            .forEach { modules.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("列出可用模块时出错", e)
        }
        
        return modules
    }
    
    /**
     * 检查指定模块是否包含指定类型的资源
     */
    fun hasModuleResource(
        modMainClass: Class<*>,
        moduleName: String,
        resourceType: String
    ): Boolean {
        return try {
            val resourceUrl = modMainClass.classLoader.getResource("assets/spark_core/$moduleName/$resourceType")
            resourceUrl != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从mod主类推断modId
     *
     * @param modMainClass mod主类
     * @return 推断的modId
     */
    private fun inferModIdFromClass(modMainClass: Class<*>): String {
        SparkCore.LOGGER.info("=== inferModIdFromClass 调试 ===")
        // 首先尝试从已注册的mod列表中查找
        val registeredMods = cn.solarmoon.spark_core.resource.common.MultiModResourceRegistry.getRegisteredMods()
        for (modInfo in registeredMods) {
            if (modInfo.modMainClass == modMainClass) {
                return modInfo.modId
            }
        }

        // 如果没找到，使用默认逻辑
        val inferredModId = when {
            modMainClass.name.contains("spark_core") -> {
                "spark_core"
            }
            else -> {
                // 尝试从包名推断
                val packageName = modMainClass.packageName
                val parts = packageName.split(".")
                val result = if (parts.size >= 3) {
                    parts[2] // 通常是 cn.solarmoon.{mod_id}
                } else {
                    "unknown_mod"
                }
                result
            }
        }
        return inferredModId
    }

    /**
     * 使用NeoForge官方API正确定位mod资源
     *
     * 这个方法解决了类加载器上下文混乱的问题，确保每个mod从正确的jar包中获取资源
     *
     * @param modId 模组ID
     * @return 该mod的assets/sparkcore/目录URL，如果未找到则返回null
     */
    private fun findCorrectResourceUrl(modId: String): URL? {
        return try {
            // 使用NeoForge官方API获取mod文件信息
            val modFileInfo = ModList.get().getModFileById(modId)
            if (modFileInfo == null) {
                return null
            }
            // 在特定mod的文件中查找assets/sparkcore/资源
            val resourcePath = modFileInfo.file.findResource("assets/sparkcore/")
            if (resourcePath == null) {
                return null
            }
            val resourceUrl = resourcePath.toUri().toURL()
            resourceUrl
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 规范化资源名称，确保符合Minecraft ResourceLocation的命名规范
     * 只允许 [a-z0-9/._-] 字符
     */
    fun normalizeResourceName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9/._-]"), "_")
            .replace(Regex("_+"), "_") // 将连续的下划线合并为单个下划线
            .trim('_') // 移除开头和结尾的下划线
    }
}