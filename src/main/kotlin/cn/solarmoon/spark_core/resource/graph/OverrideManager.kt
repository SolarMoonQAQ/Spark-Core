package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import org.jgrapht.graph.SimpleDirectedGraph
import java.util.concurrent.ConcurrentHashMap

/**
 * 覆盖管理器
 * 
 * 专门负责资源覆盖逻辑的管理，遵循单一职责原则。
 * 提供覆盖规则的管理、覆盖关系的解析和优先级处理功能。
 */
object OverrideManager {

    // 覆盖关系缓存
    private val overrideCache = ConcurrentHashMap<ResourceLocation, List<ResourceNode>>()
    private val allOverridesCache = ConcurrentHashMap<String, Map<ResourceNode, List<ResourceNode>>>()

    // 覆盖系统状态管理
    private var isInitialized = false
    private var pendingChanges = false
    private val changeDebounceDelay = 500L // 500ms防抖延迟
    private var lastChangeTime = 0L

    // 变动监听器
    private val changeListeners = mutableListOf<() -> Unit>()
    
    /**
     * 添加一个覆盖规则到图中
     *
     * @param source 覆盖资源的位置
     * @param target 被覆盖资源的位置
     * @return 是否成功添加覆盖规则
     */
    fun addOverride(source: ResourceLocation, target: ResourceLocation): Boolean {
        val sourceNode = ResourceNodeManager.getNode(source)
        val targetNode = ResourceNodeManager.getNode(target)
        if (sourceNode != null && targetNode != null) {
            // 清除相关缓存
            clearCacheForResource(target)
            SparkCore.LOGGER.debug("添加覆盖规则: $source -> $target")

            // 通知覆盖图变动
            notifyOverrideChange()
            return true
        }
        SparkCore.LOGGER.warn("无法添加覆盖规则，资源节点不存在: $source -> $target")
        return false
    }
    
    /**
     * 在图中添加覆盖边
     * 
     * @param graph 依赖图
     * @param source 覆盖资源的位置
     * @param target 被覆盖资源的位置
     * @return 是否成功添加覆盖边
     */
    fun addOverrideToGraph(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        source: ResourceLocation,
        target: ResourceLocation
    ): Boolean {
        val sourceNode = ResourceNodeManager.getNode(source)
        val targetNode = ResourceNodeManager.getNode(target)
        if (sourceNode != null && targetNode != null) {
            try {
                graph.addEdge(sourceNode, targetNode, EdgeType.OVERRIDE)
                // 清除相关缓存
                clearCacheForResource(target)
                SparkCore.LOGGER.debug("在图中添加覆盖边: $source -> $target")

                // 通知覆盖图变动
                notifyOverrideChange()
                return true
            } catch (e: Exception) {
                SparkCore.LOGGER.error("添加覆盖边失败: $source -> $target", e)
                return false
            }
        }
        SparkCore.LOGGER.warn("无法添加覆盖边，资源节点不存在: $source -> $target")
        return false
    }
    
    /**
     * 移除覆盖规则
     * 
     * @param graph 依赖图
     * @param source 覆盖资源的位置
     * @param target 被覆盖资源的位置
     * @return 是否成功移除覆盖规则
     */
    fun removeOverride(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        source: ResourceLocation, 
        target: ResourceLocation
    ): Boolean {
        val sourceNode = ResourceNodeManager.getNode(source)
        val targetNode = ResourceNodeManager.getNode(target)
        if (sourceNode != null && targetNode != null) {
            try {
                val edge = graph.getEdge(sourceNode, targetNode)
                if (edge == EdgeType.OVERRIDE) {
                    graph.removeEdge(edge)
                    // 清除相关缓存
                    clearCacheForResource(target)
                    SparkCore.LOGGER.debug("移除覆盖边: $source -> $target")
                    return true
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("移除覆盖边失败: $source -> $target", e)
            }
        }
        return false
    }
    
    /**
     * 获取一个资源的所有覆盖规则 (即，哪些资源覆盖了它)
     * 
     * @param graph 依赖图
     * @param resourceId 资源ID
     * @return 覆盖该资源的节点列表
     */
    fun getOverridesFor(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        resourceId: ResourceLocation
    ): List<ResourceNode> {
        // 检查缓存
        val cached = overrideCache[resourceId]
        if (cached != null) {
            return cached
        }
        
        val node = ResourceNodeManager.getNode(resourceId) ?: return emptyList()
        val overrides = graph.incomingEdgesOf(node)
            .filter { it == EdgeType.OVERRIDE }
            .map { graph.getEdgeSource(it) }
        
        // 缓存结果
        overrideCache[resourceId] = overrides
        return overrides
    }
    
    /**
     * 获取所有覆盖规则
     * 
     * @param graph 依赖图
     * @return 所有覆盖关系的映射
     */
    fun getAllOverrides(graph: SimpleDirectedGraph<ResourceNode, EdgeType>): Map<ResourceNode, List<ResourceNode>> {
        val cacheKey = "all_overrides"
        val cached = allOverridesCache[cacheKey]
        if (cached != null) {
            return cached
        }
        
        val result = graph.vertexSet().associateWith { node ->
            graph.incomingEdgesOf(node)
                .filter { it == EdgeType.OVERRIDE }
                .map { graph.getEdgeSource(it) }
        }.filter { it.value.isNotEmpty() }
        
        // 缓存结果
        allOverridesCache[cacheKey] = result
        return result
    }
    
    /**
     * 解析资源覆盖
     * 根据模块优先级和依赖图中的覆盖关系返回最终应该使用的资源
     *
     * @param graph 依赖图
     * @param resourceLocation 要解析的资源位置
     * @return 解析后的资源位置
     */
    fun resolveResourceOverride(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        resourceLocation: ResourceLocation
    ): ResourceLocation {
        return try {
            // 从图中获取所有覆盖此资源的节点
            val overrideNodes = getOverridesFor(graph, resourceLocation)

            if (overrideNodes.isEmpty()) {
                return resourceLocation
            }

            // 如果只有一个覆盖，直接返回
            if (overrideNodes.size == 1) {
                val overrideResource = overrideNodes.first().id
                SparkCore.LOGGER.debug("资源覆盖解析: {} -> {}", resourceLocation, overrideResource)
                return overrideResource
            }

            // 多个覆盖存在冲突，使用模块依赖图解决
            resolveOverrideConflict(resourceLocation, overrideNodes)
        } catch (e: Exception) {
            SparkCore.LOGGER.warn("资源覆盖解析失败，返回原始资源: $resourceLocation", e)
            resourceLocation
        }
    }
    
    /**
     * 检查资源是否被覆盖
     * 
     * @param graph 依赖图
     * @param resourceLocation 资源位置
     * @return 是否被覆盖
     */
    fun isResourceOverridden(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        resourceLocation: ResourceLocation
    ): Boolean {
        return getOverridesFor(graph, resourceLocation).isNotEmpty()
    }
    
    /**
     * 获取覆盖链
     * 返回从原始资源到最终覆盖资源的完整链路
     *
     * @param graph 依赖图
     * @param resourceLocation 起始资源位置
     * @return 覆盖链列表
     */
    fun getOverrideChain(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        resourceLocation: ResourceLocation
    ): List<ResourceLocation> {
        return try {
            val chain = mutableListOf<ResourceLocation>()
            var currentResource = resourceLocation
            val visited = mutableSetOf<ResourceLocation>()

            // 构建覆盖链，避免循环
            while (currentResource !in visited) {
                visited.add(currentResource)
                chain.add(currentResource)

                val overrides = getOverridesFor(graph, currentResource)
                if (overrides.isEmpty()) {
                    break
                }

                // 如果有多个覆盖，选择优先级最高的
                currentResource = if (overrides.size == 1) {
                    overrides.first().id
                } else {
                    resolveOverrideConflict(currentResource, overrides)
                }
            }

            chain
        } catch (e: Exception) {
            SparkCore.LOGGER.warn("获取覆盖链失败: $resourceLocation", e)
            listOf(resourceLocation)
        }
    }
    
    /**
     * 获取覆盖统计信息
     * 
     * @param graph 依赖图
     * @param resourceLocation 资源位置
     * @return 覆盖统计信息
     */
    fun getOverrideStatistics(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        resourceLocation: ResourceLocation
    ): OverrideStatistics {
        val overrides = getOverridesFor(graph, resourceLocation)
        val modules = overrides.map { it.namespace }.distinct()
        val namespaces = overrides.map { it.namespace }.distinct()
        
        return OverrideStatistics(
            isOverridden = overrides.isNotEmpty(),
            overrideCount = overrides.size,
            topPriority = modules.firstOrNull(), // 简化实现，实际应该根据模块优先级确定
            modules = modules,
            namespaces = namespaces
        )
    }
    
    /**
     * 解决覆盖冲突
     * 当多个模块都覆盖同一个资源时，根据模块优先级选择最终的覆盖者
     *
     * @param originalResource 原始资源位置
     * @param overrideNodes 所有覆盖节点
     * @return 最终选择的资源位置
     */
    private fun resolveOverrideConflict(
        originalResource: ResourceLocation,
        overrideNodes: List<ResourceNode>
    ): ResourceLocation {
        val conflictingModules = overrideNodes.map { it.namespace }.distinct()

        // 获取模块的拓扑排序，优先级高的模块在后面
        val sortedModules = ModuleGraphManager.getModulesInTopologicalOrder().map { it.id }

        // 找到优先级最高的模块（在拓扑排序中位置最靠后的）
        val chosenModule = conflictingModules.maxByOrNull { module ->
            val index = sortedModules.indexOf(module)
            if (index == -1) -1 else index // 未找到的模块优先级最低
        } ?: conflictingModules.first()

        // 找到选中模块的覆盖资源
        val chosenOverride = overrideNodes.find { it.namespace == chosenModule }
        if (chosenOverride != null) {
            SparkCore.LOGGER.debug(
                "资源覆盖解析: {} -> {} (选中模块: {})",
                originalResource,
                chosenOverride.id,
                chosenModule
            )
            return chosenOverride.id
        }

        // 兜底方案：按字母排序选择第一个
        val fallbackOverride = overrideNodes.minByOrNull { it.namespace }!!
        SparkCore.LOGGER.warn(
            "资源覆盖解析使用兜底方案: $originalResource -> ${fallbackOverride.id} (模块: ${fallbackOverride.namespace})"
        )
        return fallbackOverride.id
    }

    /**
     * 批量添加覆盖规则
     *
     * @param graph 依赖图
     * @param overrides 覆盖规则列表 (source -> target)
     * @return 成功添加的数量
     */
    fun addOverrides(
        graph: SimpleDirectedGraph<ResourceNode, EdgeType>,
        overrides: List<Pair<ResourceLocation, ResourceLocation>>
    ): Int {
        var successCount = 0
        overrides.forEach { (source, target) ->
            if (addOverrideToGraph(graph, source, target)) {
                successCount++
            }
        }
        SparkCore.LOGGER.debug("批量添加覆盖规则完成: $successCount/${overrides.size}")
        return successCount
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        overrideCache.clear()
        allOverridesCache.clear()
        SparkCore.LOGGER.debug("OverrideManager 缓存已清理")
    }
    
    /**
     * 清除特定资源的缓存
     */
    private fun clearCacheForResource(resourceLocation: ResourceLocation) {
        overrideCache.remove(resourceLocation)
        allOverridesCache.clear() // 简化实现，清除所有缓存
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStatistics(): Map<String, Any> {
        return mapOf(
            "override_cache_size" to overrideCache.size,
            "all_overrides_cache_size" to allOverridesCache.size
        )
    }
    
    /**
     * 覆盖统计信息
     */
    data class OverrideStatistics(
        val isOverridden: Boolean,
        val overrideCount: Int,
        val topPriority: String?,
        val modules: List<String>,
        val namespaces: List<String>
    )
    /**
     * 初始化覆盖系统
     * 在依赖图初始化完成后调用
     */
    fun initializeOverrideSystem() {
        if (isInitialized) {
            SparkCore.LOGGER.warn("覆盖系统已经初始化，跳过重复初始化")
            return
        }

        SparkCore.LOGGER.info("初始化覆盖系统...")
        isInitialized = true

        // 首次应用所有覆盖规则
        applyAllOverrides()

        SparkCore.LOGGER.info("覆盖系统初始化完成")
    }

    /**
     * 添加覆盖变动监听器
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    /**
     * 通知覆盖图变动
     */
    private fun notifyOverrideChange() {
        if (!isInitialized) {
            SparkCore.LOGGER.debug("覆盖系统未初始化，跳过变动通知")
            return
        }

        lastChangeTime = System.currentTimeMillis()
        pendingChanges = true

        // 使用防抖机制，避免频繁触发
        Thread {
            Thread.sleep(changeDebounceDelay)
            if (pendingChanges && System.currentTimeMillis() - lastChangeTime >= changeDebounceDelay) {
                pendingChanges = false
                SparkCore.LOGGER.debug("覆盖图变动检测到，触发注册表更新")
                applyAllOverrides()

                // 通知所有监听器
                changeListeners.forEach { it.invoke() }
            }
        }.start()
    }

    /**
     * 应用所有覆盖规则到注册表
     */
    private fun applyAllOverrides() {
        try {
            val appliedCount = RegistryPatcher.applyOverridesToRegistries()
            SparkCore.LOGGER.info("覆盖系统应用完成，共应用 $appliedCount 个覆盖规则")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("应用覆盖规则失败", e)
        }
    }

    /**
     * 重置覆盖系统状态
     */
    fun resetOverrideSystem() {
        isInitialized = false
        pendingChanges = false
        changeListeners.clear()
        SparkCore.LOGGER.info("覆盖系统已重置")
    }

    /**
     * 检查覆盖系统是否已初始化
     */
    fun isOverrideSystemInitialized(): Boolean {
        return isInitialized
    }
}
