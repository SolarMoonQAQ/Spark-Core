package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import net.minecraft.resources.ResourceLocation

/**
 * JavaScript资源路径API
 *
 * 为JavaScript环境提供便捷的资源路径构建服务
 * 统一的API风格，与JSEntity等其他JS API保持一致
 * 使用Mozilla Rhino JavaScript引擎
 */
object JSResourcePath : JSComponent() {
    
    /**
     * 构建动画资源路径
     *
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityPath 实体路径
     * @param animationName 动画名称
     * @return ResourceLocation字符串格式
     */
    fun buildAnimationPath(
        modId: String,
        moduleName: String,
        entityPath: String,
        animationName: String
    ): String {
        val location = SparkResourcePathBuilder.buildAnimationPath(modId, moduleName, entityPath, animationName)
        return location.toString()
    }
    
    /**
     * 构建模型资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param entityPath 实体路径
     * @return ResourceLocation字符串格式
     */

    fun buildModelPath(
        modId: String,
        moduleName: String,
        entityPath: String
    ): String {
        val location = SparkResourcePathBuilder.buildModelPath(modId, moduleName, entityPath)
        return location.toString()
    }
    
    /**
     * 构建贴图资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param texturePath 贴图路径
     * @return ResourceLocation字符串格式
     */
    fun buildTexturePath(
        modId: String,
        moduleName: String,
        texturePath: String
    ): String {
        val location = SparkResourcePathBuilder.buildTexturePath(modId, moduleName, texturePath)
        return location.toString()
    }
    
    /**
     * 构建脚本资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param scriptPath 脚本路径
     * @return ResourceLocation字符串格式
     */
    fun buildScriptPath(
        modId: String,
        moduleName: String,
        scriptPath: String
    ): String {
        val location = SparkResourcePathBuilder.buildScriptPath(modId, moduleName, scriptPath)
        return location.toString()
    }
    
    /**
     * 构建IK约束资源路径
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param constraintPath 约束路径
     * @return ResourceLocation字符串格式
     */
    fun buildIKConstraintPath(
        modId: String,
        moduleName: String,
        constraintPath: String
    ): String {
        val location = SparkResourcePathBuilder.buildIKConstraintPath(modId, moduleName, constraintPath)
        return location.toString()
    }
    
    /**
     * 通用资源路径构建器
     * 
     * @param modId 模组ID
     * @param moduleName 模块名称
     * @param resourceType 资源类型
     * @param resourcePath 资源路径
     * @return ResourceLocation字符串格式
     */
    fun buildResourcePath(
        modId: String,
        moduleName: String,
        resourceType: String,
        resourcePath: String
    ): String {
        val location = SparkResourcePathBuilder.buildResourcePath(modId, moduleName, resourceType, resourcePath)
        return location.toString()
    }


    /**
     * 检查是否是默认的player模型路径
     *
     * @param modelPath 模型路径字符串
     * @return 是否是默认player模型
     */
    fun isDefaultPlayerModel(modelPath: String): Boolean {
        val location = ResourceLocation.parse(modelPath)
        return SparkResourcePathBuilder.isDefaultPlayerModel(location)
    }

    /**
     * 路径规范化
     * 
     * @param path 原始路径
     * @return 规范化后的路径
     */
    fun normalizePath(path: String): String {
        return SparkResourcePathBuilder.normalizePath(path)
    }
}
