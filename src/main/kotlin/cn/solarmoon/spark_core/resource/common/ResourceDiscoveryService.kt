package cn.solarmoon.spark_core.resource.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.discovery.ResourceDiscoveryService as CoreResourceDiscoveryService
import cn.solarmoon.spark_core.resource.graph.ResourceSourceType
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一的资源发现服务（包装器）
 * 简化并标准化资源发现逻辑，支持多种资源源类型
 * 
 * 注意：这是对 cn.solarmoon.spark_core.resource.discovery.ResourceDiscoveryService 的包装
 */
object ResourceDiscoveryService {
    
    private val discoveredPaths = ConcurrentHashMap<String, MutableSet<Path>>()
    private val moduleSourceMapping = ConcurrentHashMap<String, ResourceSourceType>()
    
    /**
     * 发现并注册资源路径
     * @param resourceType 资源类型 (如 "animations", "models")
     * @return 发现的路径集合
     */
    fun discoverResourcePaths(resourceType: String): Set<Path> {
        val paths = mutableSetOf<Path>()
        
        // 1. 从核心ResourceDiscoveryService获取命名空间
        val discoveredNamespaces = CoreResourceDiscoveryService.getDiscoveredNamespaces()
        
        for ((namespace, namespaceInfo) in discoveredNamespaces.entries) {
            if (resourceType in namespaceInfo.resourceTypes) {
                val resourcePath = when (namespaceInfo.type) {
                    CoreResourceDiscoveryService.ResourceSourceType.LOOSE_FILES -> {
                        namespaceInfo.rootPath.resolve(resourceType)
                    }
                    CoreResourceDiscoveryService.ResourceSourceType.MOD_ASSETS -> {
                        namespaceInfo.rootPath.resolve("assets").resolve(namespace).resolve(resourceType)
                    }
                    CoreResourceDiscoveryService.ResourceSourceType.SPARK_PACKAGE -> {
                        // TODO: 实现.spark包文件解析
                        continue
                    }
                    else -> continue
                }
                
                // 只为已存在的路径添加到发现列表，不主动创建扁平目录
                if (Files.exists(resourcePath)) {
                    paths.add(resourcePath)
                } else {
                    SparkCore.LOGGER.debug("资源路径 $resourcePath 不存在，跳过")
                }
                
                // 记录命名空间与源类型的映射
                moduleSourceMapping[namespace] = when (namespaceInfo.type) {
                    CoreResourceDiscoveryService.ResourceSourceType.LOOSE_FILES -> ResourceSourceType.LOOSE_FILES
                    CoreResourceDiscoveryService.ResourceSourceType.MOD_ASSETS -> ResourceSourceType.MOD_ASSETS
                    else -> ResourceSourceType.SPARK_PACKAGE
                }
            }
        }
        
        // 缓存发现的路径
        discoveredPaths[resourceType] = paths.toMutableSet()
        
        SparkCore.LOGGER.info("发现 $resourceType 资源路径 ${paths.size} 个: ${paths.joinToString(", ")}")
        return paths
    }

    
    /**
     * 扫描路径下的所有资源文件
     * @param basePath 基础路径
     * @param supportedExtensions 支持的扩展名集合
     * @return 发现的资源文件路径集合
     */
    fun scanResourceFiles(basePath: Path, supportedExtensions: Set<String>): Set<Path> {
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            return emptySet()
        }
        
        return try {
            Files.walk(basePath).use { pathStream ->
                pathStream
                    .filter { Files.isRegularFile(it) }
                    .filter { path ->
                        val fileName = path.fileName.toString().lowercase()
                        supportedExtensions.any { ext -> fileName.endsWith(".$ext") } &&
                        !fileName.endsWith(".meta.json") &&
                        !fileName.startsWith(".")
                    }
                    .collect(java.util.stream.Collectors.toSet())
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("扫描资源文件失败: $basePath", e)
            emptySet()
        }
    }
}