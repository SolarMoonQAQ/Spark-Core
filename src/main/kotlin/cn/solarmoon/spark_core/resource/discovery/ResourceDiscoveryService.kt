package cn.solarmoon.spark_core.resource.discovery

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object ResourceDiscoveryService {
    
    data class NamespaceInfo(
        val namespace: String,
        val rootPath: Path,
        val type: ResourceSourceType,
        val resourceTypes: Set<String>,
        val discoveredAt: Long
    )
    
    enum class ResourceSourceType {
        LOOSE_FILES,
        SPARK_PACKAGE,
        MOD_ASSETS
    }
    
    private val discoveredNamespaces = ConcurrentHashMap<String, NamespaceInfo>()
    
    private val supportedResourceTypes = setOf(
        "animations", "models", "textures", "scripts", "sounds", 
        "ik_constraints", "lang", "shaders"
    )
    
    fun initialize() {
        scanGameDirectoryResources()
        SparkCore.LOGGER.info("资源发现完成，发现 ${discoveredNamespaces.size} 个命名空间")
        discoveredNamespaces.forEach { (namespace, info) ->
            SparkCore.LOGGER.info("  - $namespace: ${info.type} (${info.rootPath})")
        }
    }
    
    fun getDiscoveredNamespaces(): Map<String, NamespaceInfo> = discoveredNamespaces.toMap()
    
    private fun scanGameDirectoryResources() {
        val gameDir = FMLPaths.GAMEDIR.get()
        val sparkcoreDir = gameDir.resolve("sparkcore")
        SparkCore.LOGGER.debug("扫描SparkCore资源目录（四层结构）: $sparkcoreDir")

        // 确保sparkcore目录存在
        if (!Files.exists(sparkcoreDir)) {
            try {
                Files.createDirectories(sparkcoreDir)
                SparkCore.LOGGER.info("创建SparkCore资源目录: $sparkcoreDir")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("无法创建SparkCore资源目录: ${e.message}", e)
                return
            }
        }

        if (!Files.isDirectory(sparkcoreDir)) {
            SparkCore.LOGGER.warn("SparkCore资源路径不是目录: $sparkcoreDir")
            return
        }

        try {
            // 四层目录结构：run/sparkcore/{modId}/{moduleName}/
            Files.list(sparkcoreDir).use { modDirs ->
                modDirs.filter { Files.isDirectory(it) }
                    .forEach { modDir ->
                        val modId = modDir.fileName.toString()

                        // 跳过隐藏目录和系统目录
                        if (modId.startsWith(".") || modId.startsWith("_")) {
                            return@forEach
                        }

                        SparkCore.LOGGER.debug("扫描mod目录: $modId")
                        scanModDirectory(modId, modDir)
                    }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描SparkCore资源目录时出错: ${e.message}", e)
        }
    }

    private fun scanModDirectory(modId: String, modDir: Path) {
        try {
            Files.list(modDir).use { moduleDirs ->
                moduleDirs.filter { Files.isDirectory(it) }
                    .forEach { moduleDir ->
                        val moduleName = moduleDir.fileName.toString()

                        // 跳过隐藏目录和系统目录
                        if (moduleName.startsWith(".") || moduleName.startsWith("_")) {
                            return@forEach
                        }

                        val fullModuleId = "$modId:$moduleName"
                        SparkCore.LOGGER.debug("扫描模块目录: $fullModuleId")
                        scanNamespaceDirectory(fullModuleId, moduleDir, ResourceSourceType.LOOSE_FILES)
                    }
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描mod目录 $modDir 时出错: ${e.message}", e)
        }
    }
    
    private fun scanNamespaceDirectory(namespace: String, namespaceDir: Path, sourceType: ResourceSourceType) {
        try {
            val resourceTypes = mutableSetOf<String>()

            Files.list(namespaceDir).use { paths ->
                paths.filter { Files.isDirectory(it) }
                    .forEach { resourceTypeDir ->
                        val resourceType = resourceTypeDir.fileName.toString()
                        if (resourceType in supportedResourceTypes) {
                            resourceTypes.add(resourceType)
                        }
                    }
            }

            if (resourceTypes.isNotEmpty()) {
                registerNamespace(namespace, namespaceDir, sourceType, resourceTypes)
                SparkCore.LOGGER.debug("发现模块: $namespace (类型: ${resourceTypes.joinToString()})")
            }

        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描命名空间目录 $namespaceDir 时出错: ${e.message}", e)
        }
    }
    
    private fun registerNamespace(
        namespace: String, 
        rootPath: Path, 
        sourceType: ResourceSourceType,
        resourceTypes: Set<String> = emptySet()
    ) {
        val namespaceInfo = NamespaceInfo(
            namespace = namespace,
            rootPath = rootPath,
            type = sourceType,
            resourceTypes = resourceTypes,
            discoveredAt = System.currentTimeMillis()
        )
        
        discoveredNamespaces[namespace] = namespaceInfo
    }
} 