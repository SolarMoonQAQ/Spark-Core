package cn.solarmoon.spark_core.resource.common

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path

/**
 * 统一的资源处理器接口
 * 标准化所有资源处理器的行为和契约
 */
interface IResourceHandler {
    
    /**
     * 获取资源类型标识符
     * 例如: "animations", "models", "textures", "scripts", "ik_constraints"
     */
    fun getResourceType(): String
    
    /**
     * 获取此处理器关联的动态注册表标识符
     */
    fun getRegistryIdentifier(): ResourceLocation?
    
    /**
     * 处理资源添加事件
     * @param filePath 资源文件路径
     */
    fun onResourceAdded(filePath: Path)
    
    /**
     * 处理资源修改事件
     * @param filePath 资源文件路径
     */
    fun onResourceModified(filePath: Path)
    
    /**
     * 处理资源删除事件
     * @param filePath 资源文件路径
     */
    fun onResourceRemoved(filePath: Path)
    
    /**
     * 验证资源文件是否被此处理器支持
     * @param filePath 文件路径
     * @return 是否支持此文件
     */
    fun canHandle(filePath: Path): Boolean
    
    /**
     * 获取支持的文件扩展名
     */
    fun getSupportedExtensions(): Set<String>
    
    /**
     * 初始化处理器
     * @param modMainClass MOD主类，用于从JAR中提取默认资源
     */
    fun initialize(modMainClass: Class<*>): Boolean
    
    /**
     * 初始化默认资源
     * @param modMainClass MOD主类，用于从JAR中提取默认资源
     */
    fun initializeDefaultResources(modMainClass: Class<*>): Boolean
    
    /**
     * 获取目录ID（用于热重载服务）
     */
    fun getDirectoryId(): String
    
    /**
     * 获取处理器优先级（用于排序）
     * 数值越小优先级越高
     */
    fun getPriority(): Int = 100
    
    /**
     * 标记初始扫描完成
     */
    fun markInitialScanComplete()
    
    /**
     * 检查初始扫描是否完成
     */
    fun isInitialScanComplete(): Boolean
}

/**
 * 模块感知的资源处理器接口
 * 为支持模块系统的处理器提供额外的生命周期管理
 */
interface IModuleAwareResourceHandler : IResourceHandler {
    
    /**
     * 注册模块资源
     * @param moduleId 模块ID
     * @param modulePath 模块根路径
     */
    fun registerModule(moduleId: String, modulePath: Path)
    
    /**
     * 注销模块资源
     * @param moduleId 模块ID
     */
    fun unregisterModule(moduleId: String)
    
    /**
     * 获取模块资源映射
     * @return 模块ID到资源集合的映射
     */
    fun getModuleResourceMapping(): Map<String, Set<ResourceLocation>>
    
    /**
     * 检查模块是否已注册
     * @param moduleId 模块ID
     */
    fun isModuleRegistered(moduleId: String): Boolean
}

/**
 * 热重载感知的资源处理器接口
 * 为支持热重载的处理器提供额外的能力
 */
interface IHotReloadAwareHandler : IResourceHandler {
    
    /**
     * 触发热重载
     * @param filePath 资源文件路径
     * @param changeType 变更类型
     */
    fun triggerHotReload(filePath: Path, changeType: ChangeType)
    
    /**
     * 检查是否支持热重载
     */
    fun supportsHotReload(): Boolean = true
    
    /**
     * 变更类型
     */
    enum class ChangeType {
        ADDED, MODIFIED, REMOVED
    }
}

/**
 * 统一的资源处理器异常
 */
sealed class ResourceHandlerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * 资源解析异常
     */
    class ResourceParseException(resourcePath: String, cause: Throwable? = null) : 
        ResourceHandlerException("资源解析失败: $resourcePath", cause)
    
    /**
     * 依赖验证异常
     */
    class DependencyValidationException(resourcePath: String, dependency: String, cause: Throwable? = null) : 
        ResourceHandlerException("依赖验证失败: $resourcePath -> $dependency", cause)
    
    /**
     * 注册表操作异常
     */
    class RegistryOperationException(operation: String, resourcePath: String, cause: Throwable? = null) : 
        ResourceHandlerException("注册表操作失败: $operation for $resourcePath", cause)
    
    /**
     * 模块操作异常
     */
    class ModuleOperationException(moduleId: String, operation: String, cause: Throwable? = null) : 
        ResourceHandlerException("模块操作失败: $operation for module $moduleId", cause)
}

/**
 * 资源处理器验证契约
 * 确保所有处理器实现都遵循相同的验证规则
 */
interface IResourceHandlerValidator {
    
    /**
     * 验证资源路径
     */
    fun validateResourcePath(filePath: Path): Boolean
    
    /**
     * 验证资源内容
     */
    fun validateResourceContent(filePath: Path): Boolean
    
    /**
     * 验证依赖关系
     */
    fun validateDependencies(filePath: Path): Boolean
}

/**
 * 资源处理器日志记录器
 * 提供统一的日志记录格式和级别
 */
object ResourceHandlerLogger {
    
    fun logResourceAdded(handlerType: String, location: ResourceLocation, moduleId: String) {
        SparkCore.LOGGER.info("[$handlerType] 资源已添加: $location [模块: $moduleId]")
    }
    
    fun logResourceModified(handlerType: String, location: ResourceLocation, moduleId: String) {
        SparkCore.LOGGER.info("[$handlerType] 资源已修改: $location [模块: $moduleId]")
    }
    
    fun logResourceRemoved(handlerType: String, location: ResourceLocation, moduleId: String) {
        SparkCore.LOGGER.info("[$handlerType] 资源已移除: $location [模块: $moduleId]")
    }
    
    fun logDependencyWarning(handlerType: String, resourceLocation: ResourceLocation, dependency: String, message: String) {
        SparkCore.LOGGER.warn("[$handlerType] 依赖警告 [$resourceLocation -> $dependency]: $message")
    }
    
    fun logDependencyError(handlerType: String, resourceLocation: ResourceLocation, dependency: String, message: String) {
        SparkCore.LOGGER.error("[$handlerType] 依赖错误 [$resourceLocation -> $dependency]: $message")
    }
    
    fun logHandlerError(handlerType: String, operation: String, resourceLocation: ResourceLocation, error: Throwable) {
        SparkCore.LOGGER.error("[$handlerType] 操作失败 [$operation] for $resourceLocation: ${error.message}", error)
    }
}