package cn.solarmoon.spark_core.js.resource

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.js.JSApi
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.extension.JSResourcePath
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

/**
 * JavaScript资源路径API
 *
 * 为JavaScript环境提供资源路径构建服务
 * 与JSEntity、JSSkillApi等保持一致的API风格
 *
 * 这个API主要作为JSResourcePath组件的管理器，
 * 实际的方法实现在JSResourcePath中
 */
object JSResourcePathApi : JSApi, JSComponent() {

    override val id: String = "resource_path"
    override val valueCache: MutableMap<String, String> = mutableMapOf()

    private val logger = LoggerFactory.getLogger(JSResourcePathApi::class.java)

    // 存储路径转换缓存，提高性能
    private val pathCache = mutableMapOf<String, String>()

    /**
     * 构建资源路径（通用方法）
     * 这是对JSResourcePath.buildResourcePath的包装
     */
    fun buildResourcePath(
        modId: String,
        moduleName: String,
        resourceType: String,
        resourcePath: String
    ): String {
        val cacheKey = "$modId:$moduleName/$resourceType/$resourcePath"
        return pathCache.getOrPut(cacheKey) {
            JSResourcePath.buildResourcePath(modId, moduleName, resourceType, resourcePath)
        }
    }

    /**
     * 构建动画路径
     */
    fun buildAnimationPath(
        modId: String,
        moduleName: String,
        entityPath: String,
        animationName: String
    ): String {
        return JSResourcePath.buildAnimationPath(modId, moduleName, entityPath, animationName)
    }

    /**
     * 构建模型路径
     */
    fun buildModelPath(
        modId: String,
        moduleName: String,
        entityPath: String
    ): String {
        return JSResourcePath.buildModelPath(modId, moduleName, entityPath)
    }

    /**
     * 构建纹理路径
     */
    fun buildTexturePath(
        modId: String,
        moduleName: String,
        texturePath: String
    ): String {
        return JSResourcePath.buildTexturePath(modId, moduleName, texturePath)
    }

    /**
     * 构建脚本路径
     */
    fun buildScriptPath(
        modId: String,
        moduleName: String,
        scriptPath: String
    ): String {
        return JSResourcePath.buildScriptPath(modId, moduleName, scriptPath)
    }

    /**
     * 验证资源路径格式
     */
    fun validateResourcePath(path: String): Boolean {
        return try {
            ResourceLocation.parse(path)
            true
        } catch (e: Exception) {
            logger.warn("无效的资源路径格式: $path", e)
            false
        }
    }

    /**
     * 获取路径统计信息
     */
    fun getPathStats(): Map<String, Any> {
        return mapOf(
            "cached_paths" to pathCache.size,
            "api_id" to id,
            "component_registered" to (engine != null)
        )
    }

    override fun onLoad() {
        logger.info("JSResourcePathApi 加载完成，缓存大小: ${pathCache.size}")

        // 预热常用路径格式
        valueCache["default_mod_id"] = "spark_core"
        valueCache["default_module_name"] = "spark_core"
        valueCache["animation_type"] = "animations"
        valueCache["model_type"] = "models"
        valueCache["texture_type"] = "textures"
        valueCache["script_type"] = "scripts"

        logger.debug("JSResourcePathApi 预设值已加载: ${valueCache.keys}")
    }

    override fun onReload() {
        logger.info("JSResourcePathApi 重载中，清理缓存...")

        // 清理路径缓存
        pathCache.clear()

        // 保留基本配置，清理动态缓存
        val basicKeys = setOf("default_mod_id", "default_module_name", "animation_type", "model_type", "texture_type", "script_type")
        valueCache.keys.retainAll(basicKeys)

        logger.info("JSResourcePathApi 重载完成")
    }
}
