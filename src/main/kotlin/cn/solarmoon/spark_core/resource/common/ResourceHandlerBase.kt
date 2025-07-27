package cn.solarmoon.spark_core.resource.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.conflict.ResourceConflictManager
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一资源处理器基础实现
 * 为所有资源处理器提供通用功能和标准化行为
 */
abstract class ResourceHandlerBase : IModuleAwareResourceHandler, IHotReloadAwareHandler, IResourceHandlerValidator {
    
    protected var initialScanComplete = false
    protected val moduleResources = ConcurrentHashMap<String, MutableSet<ResourceLocation>>()
    
    // 实现IResourceHandler的基础方法
    override fun onResourceAdded(filePath: Path) {
        val node = ResourceGraphManager.addOrUpdateResource(filePath, getResourceType())
        if (node != null) {
            try {
                processResourceAdded(node)
                ResourceHandlerLogger.logResourceAdded(getResourceType(), node.id, node.getFullModuleId())
            } catch (e: Exception) {
                ResourceHandlerLogger.logHandlerError(getResourceType(), "ADD", node.id, e)
                throw e
            }
        }
    }
    
    override fun onResourceModified(filePath: Path) {
        val node = ResourceGraphManager.addOrUpdateResource(filePath, getResourceType())
        if (node != null) {
            try {
                // 检测资源冲突
                val conflictResult = ResourceConflictManager.detectConflict(node)
                when (conflictResult) {
                    is ResourceConflictManager.ConflictDetectionResult.Conflict -> {
                        SparkCore.LOGGER.warn("检测到资源冲突: ${node.id}")
                        // 暂停处理，等待用户解决冲突
                        return
                    }
                    is ResourceConflictManager.ConflictDetectionResult.PendingChange -> {
                        SparkCore.LOGGER.info("资源有变更，已创建Legacy备份: ${node.id}")
                    }
                    else -> {
                        // 无冲突，正常处理
                    }
                }

                processResourceModified(node)
                ResourceHandlerLogger.logResourceModified(getResourceType(), node.id, node.getFullModuleId())
            } catch (e: Exception) {
                ResourceHandlerLogger.logHandlerError(getResourceType(), "MODIFY", node.id, e)
                throw e
            }
        }
    }
    
    override fun onResourceRemoved(filePath: Path) {
        // 对于删除，我们无法轻易地从路径反向解析出完整的ResourceLocation，
        // 这是一个新架构需要解决的问题。
        // 暂时策略：尝试让GraphManager处理，如果它能找到并删除，就继续。
        val node = ResourceGraphManager.findNodeByPath(filePath) // 需要在GraphManager中实现此方法
        if (node != null) {
            try {
                ResourceGraphManager.removeResource(node.id)
                processResourceRemoved(node)
                ResourceHandlerLogger.logResourceRemoved(getResourceType(), node.id, node.getFullModuleId())
            } catch (e: Exception) {
                ResourceHandlerLogger.logHandlerError(getResourceType(), "REMOVE", node.id, e)
                throw e
            }
        }
    }
    
    // 抽象方法，具体处理器需要实现
    protected abstract fun processResourceAdded(node: cn.solarmoon.spark_core.resource.graph.ResourceNode)
    protected abstract fun processResourceModified(node: cn.solarmoon.spark_core.resource.graph.ResourceNode)
    protected abstract fun processResourceRemoved(node: cn.solarmoon.spark_core.resource.graph.ResourceNode)
    
    // 实现IResourceHandlerValidator接口
    override fun validateResourcePath(filePath: Path): Boolean {
        // 基础验证：检查文件是否被此处理器支持
        return canHandle(filePath)
    }
    
    override fun validateResourceContent(filePath: Path): Boolean {
        // 默认实现：总是返回true，子类可以覆盖
        return true
    }
    
    override fun validateDependencies(filePath: Path): Boolean {
        // 此方法在新架构下不再需要，依赖由ResourceGraphManager统一管理
        // 暂时返回true以保持兼容
        return true
    }
    
    // 实现IResourceHandler的基础方法
    override fun markInitialScanComplete() {
        initialScanComplete = true
        SparkCore.LOGGER.info("[${getResourceType()}] 初始扫描完成")
    }
    
    override fun isInitialScanComplete(): Boolean {
        return initialScanComplete
    }
    
    override fun canHandle(filePath: Path): Boolean {
        val fileName = filePath.fileName.toString().lowercase()
        return getSupportedExtensions().any { ext -> fileName.endsWith(ext) }
    }
    
    override fun getPriority(): Int = 100
    
    override fun getDirectoryId(): String = getResourceType()
    
    override fun initializeDefaultResources(modMainClass: Class<*>): Boolean {
        // 默认实现：委托给initialize方法
        return initialize(modMainClass)
    }
    
    // 实现IModuleAwareResourceHandler接口
    override fun registerModule(moduleId: String, modulePath: Path) {
        try {
            moduleResources.computeIfAbsent(moduleId) { ConcurrentHashMap.newKeySet() }
            SparkCore.LOGGER.info("[${getResourceType()}] 注册模块: $moduleId，路径: $modulePath")
        } catch (e: Exception) {
            throw ResourceHandlerException.ModuleOperationException(moduleId, "register", e)
        }
    }
    
    override fun unregisterModule(moduleId: String) {
        try {
            val resources = moduleResources.remove(moduleId)
            if (resources != null) {
                SparkCore.LOGGER.info("[${getResourceType()}] 注销模块: $moduleId，资源数量: ${resources.size}")
                
                // 清理模块资源
                resources.forEach { resourceLocation ->
                    try {
                        cleanupModuleResource(resourceLocation)
                    } catch (e: Exception) {
                        SparkCore.LOGGER.warn("[${getResourceType()}] 清理模块资源失败: $resourceLocation", e)
                    }
                }
            }
        } catch (e: Exception) {
            throw ResourceHandlerException.ModuleOperationException(moduleId, "unregister", e)
        }
    }
    
    override fun getModuleResourceMapping(): Map<String, Set<ResourceLocation>> {
        return moduleResources.mapValues { it.value.toSet() }
    }
    
    override fun isModuleRegistered(moduleId: String): Boolean {
        return moduleResources.containsKey(moduleId)
    }
    
    // 实现IHotReloadAwareHandler接口
    override fun triggerHotReload(filePath: Path, changeType: IHotReloadAwareHandler.ChangeType) {
        // 直接使用文件路径调用相应的处理方法
        when (changeType) {
            IHotReloadAwareHandler.ChangeType.ADDED -> onResourceAdded(filePath)
            IHotReloadAwareHandler.ChangeType.MODIFIED -> onResourceModified(filePath)
            IHotReloadAwareHandler.ChangeType.REMOVED -> onResourceRemoved(filePath)
        }
    }
    
    override fun supportsHotReload(): Boolean = true
    
    // 清理模块资源的方法
    protected open fun cleanupModuleResource(resourceLocation: ResourceLocation) {
        // 默认实现：什么都不做，子类可以覆盖
    }
    
    // 通用工具方法
    protected fun addResourceToModule(moduleId: String, resourceLocation: ResourceLocation) {
        moduleResources.computeIfAbsent(moduleId) { ConcurrentHashMap.newKeySet() }
            .add(resourceLocation)
    }
    
    protected fun removeResourceFromModule(moduleId: String, resourceLocation: ResourceLocation) {
        moduleResources[moduleId]?.remove(resourceLocation)
    }
    
    // 清理方法
    open fun cleanup() {
        moduleResources.clear()
        initialScanComplete = false
    }
}