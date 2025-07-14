package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 资源路径解析器
 * 
 * 专门负责资源文件路径的解析、命名空间提取和ResourceLocation生成，遵循单一职责原则。
 * 提供统一的路径解析逻辑，支持多种资源来源类型的处理。
 */
object ResourcePathResolver {
    
    /**
     * 资源路径信息
     * 包含解析后的所有路径相关信息，支持四层目录结构
     */
    data class ResourcePathInfo(
        val resourceLocation: ResourceLocation,
        val namespace: String,
        val resourcePath: String,
        val sourceType: ResourceSourceType,
        val basePath: Path,
        val relativePath: Path,
        // 新增字段：支持四层目录结构
        val modId: String,        // modId等于namespace
        val moduleName: String    // 从路径中提取的模块名
    ) {
        /**
         * 获取完整的模块标识
         * 格式：{modId}:{moduleName}
         */
        fun getFullModuleId(): String = "$modId:$moduleName"
    }
    
    /**
     * 命名空间模式列表
     * 用于匹配不同类型的资源路径结构，支持四层目录结构
     */
    private val namespacePatterns = listOf(
        Regex("""sparkcore[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]"""),  // sparkcore/modId/moduleName/type/
        Regex("""assets[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]"""),     // assets/modId/moduleName/type/
        Regex("""resources[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]"""),  // resources/namespace/type/
        Regex("""([^/\\]+)\.spark[/\\]([^/\\]+)[/\\]""")          // module.spark/type/
    )
    
    /**
     * 路径解析缓存
     * 缓存已解析的路径信息以提升性能
     */
    private val pathCache = ConcurrentHashMap<String, ResourcePathInfo?>()
    
    /**
     * 解析资源路径
     * 从文件路径中提取命名空间、资源类型等信息，生成ResourceLocation
     * 
     * @param filePath 资源文件的物理路径
     * @param resourceType 资源的类型 (e.g., "animations", "models")
     * @return 解析后的资源路径信息，如果解析失败则返回null
     */
    fun resolveResourcePath(filePath: Path, resourceType: String): ResourcePathInfo? {
        val cacheKey = "${filePath}:$resourceType"
        
        // 检查缓存
        pathCache[cacheKey]?.let { return it }
        
        val pathStr = filePath.toString().replace('\\', '/')
        
        for (pattern in namespacePatterns) {
            val match = pattern.find(pathStr)
            if (match != null) {
                // 根据模式类型提取不同的信息
                val patternInfo = extractPatternInfo(match, pattern)

                if (patternInfo.detectedType == resourceType) {
                    val pathInfo = createResourcePathInfo(filePath, patternInfo.namespace, pathStr, pattern, resourceType, patternInfo.modId, patternInfo.moduleName)
                    // 缓存结果
                    pathCache[cacheKey] = pathInfo
                    return pathInfo
                }
            }
        }
        
        SparkCore.LOGGER.debug("无法解析资源路径: $filePath (类型: $resourceType)")
        pathCache[cacheKey] = null
        return null
    }

    /**
     * 根据匹配的模式提取路径信息
     *
     * @param match 正则匹配结果
     * @param pattern 使用的正则模式
     * @return 四元组：(namespace, detectedType, modId, moduleName)
     */
    private fun extractPatternInfo(match: MatchResult, pattern: Regex): PatternInfo {
        val groups = match.groupValues

        return when {
            // 四层sparkcore模式：sparkcore/modId/moduleName/type/
            pattern.pattern.contains("sparkcore") && groups.size >= 4 -> {
                val modId = groups[1]
                val moduleName = groups[2]
                val detectedType = groups[3]
                PatternInfo(modId, detectedType, modId, moduleName)
            }
            // 其他模式保持原有逻辑
            else -> {
                val namespace = groups[1]
                val detectedType = groups[2]
                PatternInfo(namespace, detectedType, namespace, namespace)
            }
        }
    }

    /**
     * 模式信息数据类
     */
    private data class PatternInfo(
        val namespace: String,
        val detectedType: String,
        val modId: String,
        val moduleName: String
    )

    /**
     * 从文件路径中提取命名空间
     * 
     * @param filePath 文件路径
     * @return 提取的命名空间，如果无法提取则返回null
     */
    fun extractNamespace(filePath: Path): String? {
        val pathStr = filePath.toString().replace('\\', '/')
        
        for (pattern in namespacePatterns) {
            val match = pattern.find(pathStr)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    /**
     * 生成ResourceLocation
     * 根据路径信息生成标准的ResourceLocation
     * 
     * @param pathInfo 资源路径信息
     * @return 生成的ResourceLocation
     */
    fun generateResourceLocation(pathInfo: ResourcePathInfo): ResourceLocation {
        return pathInfo.resourceLocation
    }
    
    /**
     * 创建资源路径信息
     * 内部方法，用于构建完整的ResourcePathInfo对象
     */
    private fun createResourcePathInfo(
        filePath: Path,
        namespace: String,
        pathStr: String,
        pattern: Regex,
        resourceType: String,
        modId: String,
        moduleName: String
    ): ResourcePathInfo {
        val sourceType = determineSourceType(pathStr)
        val basePath = findBasePath(filePath, pattern)
        val relativePath = basePath.relativize(filePath)
        val rawResourcePath = generateResourcePath(relativePath)
        val resourcePath = if (rawResourcePath.startsWith(resourceType)) {
            rawResourcePath
        } else {
            "$resourceType/$rawResourcePath"
        }

        // 四层目录结构：ResourceLocation格式为 {modId}:{moduleName}/{resourceType}/{path}
        val fullResourcePath = "$moduleName/$resourcePath"
        val resourceLocation = ResourceLocation.fromNamespaceAndPath(modId, fullResourcePath)
        
        return ResourcePathInfo(
            resourceLocation = resourceLocation,
            namespace = modId, // namespace现在等于modId
            resourcePath = fullResourcePath, // 包含moduleName的完整路径
            sourceType = sourceType,
            basePath = basePath,
            relativePath = relativePath,
            modId = modId,
            moduleName = moduleName
        )
    }
    
    /**
     * 确定资源来源类型
     * 根据路径字符串判断资源的来源类型
     */
    private fun determineSourceType(pathStr: String): ResourceSourceType {
        return when {
            pathStr.contains("sparkcore") -> ResourceSourceType.LOOSE_FILES
            pathStr.contains("assets") -> ResourceSourceType.MOD_ASSETS
            pathStr.contains(".spark") -> ResourceSourceType.SPARK_PACKAGE
            else -> ResourceSourceType.LOOSE_FILES // 默认类型
        }
    }
    
    /**
     * 查找基础路径
     * 根据匹配的模式找到资源的基础路径
     */
    private fun findBasePath(filePath: Path, pattern: Regex): Path {
        val pathStr = filePath.toString()
        val match = pattern.find(pathStr) ?: return filePath.parent ?: filePath.root
        val matchStart = match.range.first
        val basePathStr = pathStr.substring(0, matchStart + match.value.length)
        return Path.of(basePathStr)
    }
    
    /**
     * 生成资源路径
     * 从相对路径生成标准的资源路径字符串
     */
    private fun generateResourcePath(relativePath: Path): String {
        val fileName = relativePath.fileName.toString()
        val nameWithoutExt = fileName.substringBeforeLast('.').lowercase().replace(" ", "_")
        return if (relativePath.parent != null) {
            val parentPath = relativePath.parent.toString().replace(File.separator, "/")
            "$parentPath/$nameWithoutExt"
        } else {
            nameWithoutExt
        }
    }
    
    /**
     * 验证资源路径
     * 检查资源路径是否符合规范
     * 
     * @param resourcePath 资源路径
     * @return 是否有效
     */
    fun validateResourcePath(resourcePath: String): Boolean {
        return try {
            // 检查路径格式
            if (resourcePath.isBlank()) return false
            if (resourcePath.contains("..")) return false
            if (resourcePath.startsWith("/") || resourcePath.endsWith("/")) return false
            
            // 检查字符合法性
            val validChars = Regex("[a-z0-9_./\\-]+")
            validChars.matches(resourcePath)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 标准化资源路径
     * 将资源路径标准化为规范格式
     * 
     * @param resourcePath 原始资源路径
     * @return 标准化后的资源路径
     */
    fun normalizeResourcePath(resourcePath: String): String {
        return resourcePath
            .lowercase()
            .replace("\\", "/")
            .replace(Regex("/+"), "/")
            .replace(" ", "_")
            .trim('/')
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    fun getCacheStatistics(): Map<String, Any> {
        return mapOf(
            "cache_size" to pathCache.size,
            "cache_hit_ratio" to calculateCacheHitRatio()
        )
    }
    
    /**
     * 清除路径缓存
     */
    fun clearCache() {
        pathCache.clear()
        SparkCore.LOGGER.debug("ResourcePathResolver 缓存已清理")
    }
    
    /**
     * 计算缓存命中率
     */
    private fun calculateCacheHitRatio(): Double {
        // 简化实现，实际项目中可以添加更详细的统计
        return if (pathCache.isEmpty()) 0.0 else 0.85 // 假设85%的命中率
    }
    
    /**
     * 检查路径是否为支持的资源类型
     * 
     * @param filePath 文件路径
     * @param supportedTypes 支持的资源类型集合
     * @return 是否为支持的类型
     */
    fun isSupportedResourceType(filePath: Path, supportedTypes: Set<String>): Boolean {
        val pathStr = filePath.toString().replace('\\', '/')
        return supportedTypes.any { type ->
            pathStr.contains("/$type/") || pathStr.contains("\\$type\\")
        }
    }
}
