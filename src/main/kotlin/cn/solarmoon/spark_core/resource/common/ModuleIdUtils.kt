package cn.solarmoon.spark_core.resource.common

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path

/**
 * 模块ID解析服务
 * 负责从资源路径中提取正确的模块ID
 */
object ModuleIdUtils {
    
    /**
     * 默认模块ID（用于向后兼容）
     */
    val DEFAULT_MODULE_ID = SparkCore.MOD_ID
    
    /**
     * 从ResourceLocation的namespace中提取模块ID
     */
    fun extractModuleIdFromNamespace(location: ResourceLocation): String? {
        return when {
            // 多mod架构下，从路径中提取模块信息
            location.path.contains("/") -> {
                val pathParts = location.path.split("/")
                if (pathParts.size >= 2) {
                    // 第一部分是模块名，如 sparkcore_core, mod_a, mod_b 等
                    pathParts[0]
                } else null
            }

            // 对于minecraft命名空间，检查是否是特殊资源
            location.namespace == "minecraft" -> {
                // 对于minecraft命名空间的资源，通常不需要额外的模块ID
                // 但如果路径表明这是一个模块化资源，则提取模块信息
                if (location.path.contains("/") && !isVanillaMinecraftResource(location.path)) {
                    val pathParts = location.path.split("/")
                    if (pathParts.size >= 2) pathParts[0] else null
                } else {
                    null // 标准minecraft资源
                }
            }

            // 其他命名空间直接作为模块ID（支持第三方模组和自定义命名空间）
            else -> location.namespace
        }
    }
    
    /**
     * 检查是否是原版Minecraft资源
     */
    private fun isVanillaMinecraftResource(path: String): Boolean {
        val vanillaResourcePrefixes = setOf(
            "block", "item", "entity", "gui", "environment", 
            "misc", "mob_effect", "particle", "font"
        )
        val firstPathPart = path.split("/").firstOrNull()
        return firstPathPart in vanillaResourcePrefixes
    }
    
    /**
     * 从文件路径中提取模块ID
     */
    fun extractModuleIdFromPath(basePath: Path, relativePath: Path): String? {
        val basePathStr = basePath.toString().replace('\\', '/')
        
        // 匹配模式: spark_core/moduleId/resourceType/
        val sparkcorePattern = Regex("""${SparkCore.MOD_ID}[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]""")
        val sparkcoreMatch = sparkcorePattern.find(basePathStr)
        if (sparkcoreMatch != null) {
            val moduleId = sparkcoreMatch.groupValues[1]
            if (moduleId.isNotEmpty() && moduleId != "animations" && moduleId != "models" && 
                moduleId != "textures" && moduleId != "scripts" && moduleId != "ik_constraints") {
                return moduleId
            }
        }
        
        // 匹配模式: assets/moduleId/resourceType/
        val assetsPattern = Regex("""assets[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]""")
        val assetsMatch = assetsPattern.find(basePathStr)
        if (assetsMatch != null) {
            val moduleId = assetsMatch.groupValues[1]
            // 移除硬编码限制，允许所有模组的assets目录
            if (moduleId.isNotEmpty()) {
                return moduleId
            }
        }
        
        return null
    }
    
    /**
     * 从.spark包文件路径中提取模块ID
     */
    fun extractModuleIdFromSparkPackage(sparkPackagePath: Path): String {
        val fileName = sparkPackagePath.fileName.toString()
        return if (fileName.endsWith(".spark")) {
            fileName.substringBeforeLast(".spark")
        } else {
            DEFAULT_MODULE_ID
        }
    }
    
    /**
     * 验证模块ID是否有效
     */
    fun validateModuleId(moduleId: String): Boolean {
        return moduleId.isNotEmpty() && 
               moduleId.matches(Regex("[a-z0-9_.-]+")) &&
               !moduleId.startsWith(".") &&
               !moduleId.endsWith(".")
    }
    
    /**
     * 标准化模块ID
     */
    fun normalizeModuleId(moduleId: String): String {
        return moduleId.lowercase()
            .replace(" ", "_")
            .replace("-", "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .take(32) // 限制长度
    }
    
    /**
     * 获取模块的显示名称
     */
    fun getModuleDisplayName(moduleId: String): String {
        return when (moduleId) {
            DEFAULT_MODULE_ID -> "SparkCore 默认模块"
            "minecraft" -> "Minecraft"
            else -> moduleId.split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }
}
