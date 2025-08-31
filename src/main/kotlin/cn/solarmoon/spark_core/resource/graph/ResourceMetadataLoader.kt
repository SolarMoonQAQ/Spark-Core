package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.origin.OAssetMetadata
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * 资源元数据加载器
 * 
 * 专门负责资源元数据的加载和处理，遵循单一职责原则。
 * 提供统一的元数据加载接口，支持多种元数据格式和来源。
 */
object ResourceMetadataLoader {

    /**
     * 元数据缓存
     * 缓存已加载的元数据以提升性能
     */
    private val metadataCache = ConcurrentHashMap<String, OAssetMetadata>()
    
    /**
     * 为指定资源文件加载其元数据
     * 
     * @param resourceFile 资源文件路径
     * @return 加载的元数据，如果加载失败则返回EMPTY
     */
    fun loadMetadataFor(resourceFile: Path): OAssetMetadata {
        val cacheKey = resourceFile.toString()
        
        // 检查缓存
        metadataCache[cacheKey]?.let { return it }
        
        val metadataFile = resourceFile.parent.resolve(
            resourceFile.fileName.toString().substringBeforeLast('.') + OAssetMetadata.META_FILE_SUFFIX
        )
        
        val metadata = if (!metadataFile.exists() || !metadataFile.isRegularFile()) {
            // 生成默认元数据
            generateDefaultMetadata(resourceFile)
        } else {
            try {
                loadMetadataFromFile(metadataFile)
            } catch (e: Exception) {
                SparkCore.LOGGER.error("读取或解析元数据文件失败: $metadataFile", e)
                generateDefaultMetadata(resourceFile)
            }
        }
        
        // 缓存结果
        metadataCache[cacheKey] = metadata
        return metadata
    }
    
    /**
     * 从文件内容加载元数据
     * 
     * @param metadataFile 元数据文件路径
     * @return 解析的元数据
     */
    private fun loadMetadataFromFile(metadataFile: Path): OAssetMetadata {
        val content = metadataFile.readText()
        return loadMetadataFromContent(content)
    }
    
    /**
     * 从字符串内容加载元数据
     * 
     * @param content JSON格式的元数据内容
     * @return 解析的元数据
     */
    fun loadMetadataFromContent(content: String): OAssetMetadata {
        return try {
            OAssetMetadata.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(content)).orThrow.first
        } catch (e: Exception) {
            SparkCore.LOGGER.error("解析元数据内容失败", e)
            OAssetMetadata.EMPTY
        }
    }
    
    /**
     * 验证元数据
     * 
     * @param metadata 要验证的元数据
     * @return 是否有效
     */
    fun validateMetadata(metadata: OAssetMetadata): Boolean {
        return try {
            // 基本验证
            if (metadata == OAssetMetadata.EMPTY) return true // 空元数据是有效的
            
            // 验证ID格式
            if (metadata.id.isNotBlank()) {
                ResourceLocation.parse(metadata.id) // 如果解析失败会抛出异常
            }
            
            // 验证provides列表
            metadata.provides.forEach { provide ->
                if (provide.isBlank()) return false
            }
            
            // 验证tags列表
            metadata.tags.forEach { tag ->
                if (tag.isBlank()) return false
            }
            
            true
        } catch (e: Exception) {
            SparkCore.LOGGER.warn("元数据验证失败: ${e.message}")
            false
        }
    }
    
    /**
     * 生成默认元数据
     * 当元数据文件不存在时，根据资源文件生成默认的元数据
     * 
     * @param resourceFile 资源文件路径
     * @return 生成的默认元数据
     */
    fun generateDefaultMetadata(resourceFile: Path): OAssetMetadata {
        val fileName = resourceFile.fileName.toString()
        val nameWithoutExt = fileName.substringBeforeLast('.')
        
        // 根据文件类型生成不同的默认元数据
        val provides = when {
            fileName.endsWith(".json") && resourceFile.toString().contains("animations") -> listOf("animation")
            fileName.endsWith(".json") && resourceFile.toString().contains("models") -> listOf("model")
            fileName.endsWith(".png") && resourceFile.toString().contains("textures") -> listOf("texture")
            fileName.endsWith(".js") && resourceFile.toString().contains("scripts") -> listOf("script")
            else -> emptyList()
        }
        
        val tags = when {
            resourceFile.toString().contains("animations") -> listOf("animation")
            resourceFile.toString().contains("models") -> listOf("model")
            resourceFile.toString().contains("textures") -> listOf("texture")
            resourceFile.toString().contains("scripts") -> listOf("script")
            resourceFile.toString().contains("ik_constraints") -> listOf("ik_constraint")
            else -> emptyList()
        }
        
        return OAssetMetadata(
            id = "", // 将在ResourceGraphManager中设置
            provides = provides,
            tags = tags,
            properties = mapOf(
                "auto_generated" to true,
                "source_file" to fileName,
                "generated_at" to System.currentTimeMillis().toString()
            )
        )
    }
    
    /**
     * 批量加载元数据
     * 
     * @param resourceFiles 资源文件列表
     * @return 加载的元数据映射
     */
    fun loadMetadataForFiles(resourceFiles: List<Path>): Map<Path, OAssetMetadata> {
        return resourceFiles.associateWith { file ->
            loadMetadataFor(file)
        }
    }
    
    /**
     * 预加载元数据到缓存
     * 
     * @param resourceFile 资源文件路径
     */
    fun preloadMetadata(resourceFile: Path) {
        loadMetadataFor(resourceFile)
    }
    
    /**
     * 清除元数据缓存
     */
    fun clearCache() {
        metadataCache.clear()
        SparkCore.LOGGER.debug("ResourceMetadataLoader 缓存已清理")
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    fun getCacheStatistics(): Map<String, Any> {
        return mapOf(
            "cache_size" to metadataCache.size,
            "cache_entries" to metadataCache.keys.toList()
        )
    }
    
    /**
     * 检查元数据文件是否存在
     * 
     * @param resourceFile 资源文件路径
     * @return 是否存在对应的元数据文件
     */
    fun hasMetadataFile(resourceFile: Path): Boolean {
        val metadataFile = resourceFile.parent.resolve(
            resourceFile.fileName.toString().substringBeforeLast('.') + OAssetMetadata.META_FILE_SUFFIX
        )
        return metadataFile.exists() && metadataFile.isRegularFile()
    }
    
    /**
     * 获取元数据文件路径
     * 
     * @param resourceFile 资源文件路径
     * @return 对应的元数据文件路径
     */
    fun getMetadataFilePath(resourceFile: Path): Path {
        return resourceFile.parent.resolve(
            resourceFile.fileName.toString().substringBeforeLast('.') + OAssetMetadata.META_FILE_SUFFIX
        )
    }
    
    /**
     * 保存元数据到文件
     * 
     * @param resourceFile 资源文件路径
     * @param metadata 要保存的元数据
     * @return 是否保存成功
     */
    fun saveMetadataFor(resourceFile: Path, metadata: OAssetMetadata): Boolean {
        return try {
            val metadataFile = getMetadataFilePath(resourceFile)
            val content = OAssetMetadata.CODEC.encodeStart(JsonOps.INSTANCE, metadata).orThrow.toString()
            metadataFile.toFile().writeText(content)
            
            // 更新缓存
            metadataCache[resourceFile.toString()] = metadata
            
            SparkCore.LOGGER.debug("元数据已保存: $metadataFile")
            true
        } catch (e: Exception) {
            SparkCore.LOGGER.error("保存元数据失败: $resourceFile", e)
            false
        }
    }
    
    /**
     * 元数据验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )
    
    /**
     * 详细验证元数据
     * 
     * @param metadata 要验证的元数据
     * @return 详细的验证结果
     */
    fun validateMetadataDetailed(metadata: OAssetMetadata): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // 验证ID格式
            if (metadata.id.isNotBlank()) {
                try {
                    ResourceLocation.parse(metadata.id)
                } catch (e: Exception) {
                    errors.add("无效的资源ID格式: ${metadata.id}")
                }
            }
            
            // 验证provides列表
            metadata.provides.forEachIndexed { index, provide ->
                if (provide.isBlank()) {
                    errors.add("provides[$index] 不能为空")
                }
            }
            
            // 验证tags列表
            metadata.tags.forEachIndexed { index, tag ->
                if (tag.isBlank()) {
                    errors.add("tags[$index] 不能为空")
                }
            }
            
            // 检查是否为自动生成的元数据
            if (metadata.properties["auto_generated"] == true) {
                warnings.add("这是自动生成的元数据，建议手动完善")
            }
            
        } catch (e: Exception) {
            errors.add("验证过程中发生异常: ${e.message}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
