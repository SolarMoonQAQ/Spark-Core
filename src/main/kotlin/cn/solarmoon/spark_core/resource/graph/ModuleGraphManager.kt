package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.ModuleGraphEvent
import cn.solarmoon.spark_core.resource.origin.OModuleInfo
import net.neoforged.neoforge.common.NeoForge
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.traverse.TopologicalOrderIterator
import java.util.concurrent.ConcurrentHashMap

/**
 * 模块图管理器 (重构后)
 *
 * 负责管理所有已注册模块的依赖关系图。
 * 这是一个被动的数据管理器，由外部系统（如HandlerDiscoveryService）填充数据。
 * 它不再执行任何文件I/O操作。
 */
object ModuleGraphManager {

    private val modules = ConcurrentHashMap<String, ModuleNode>()
    private val graph = DirectedAcyclicGraph<ModuleNode, DefaultEdge>(DefaultEdge::class.java)
    private val moduleWeights = ConcurrentHashMap<String, Int>()
    // 不可手动调整顺序, 通过设置权重间接调整。
    private var orderedModule: List<ModuleNode> = emptyList()
    /**
     * 注册一个新模块并构建其依赖关系。
     * 这是向管理器添加模块的唯一入口点。
     *
     * @param moduleInfo 要注册的模块的静态数据。
     */
    fun registerModule(moduleInfo: OModuleInfo) {
        val moduleNode = ModuleNode(moduleInfo)
        modules[moduleNode.id] = moduleNode
        graph.addVertex(moduleNode)
        SparkCore.LOGGER.debug("模块节点已注册: ${moduleNode.id}")

        // 发布模块注册事件
        publishModuleChangeEvent(moduleNode, ModuleGraphEvent.ChangeType.ADDED)
    }

    /**
     * 在所有模块都注册后，构建完整的依赖图。
     */
    fun buildDependencyGraph() {
        modules.values.forEach { moduleNode ->
            try {
                // 处理硬依赖
                moduleNode.data.dependencies.forEach { depId ->
                    modules[depId]?.let { depNode ->
                        graph.addEdge(moduleNode, depNode)
                        // 发布硬依赖添加事件
                        publishModuleDependencyChangeEvent(
                            moduleNode, depNode,
                            ModuleGraphEvent.DependencyType.HARD,
                            ModuleGraphEvent.ChangeType.ADDED
                        )
                    } ?: SparkCore.LOGGER.warn("模块 ${moduleNode.id} 的硬依赖 $depId 未找到。")
                }
                // 处理软依赖 (同样作为图的边)
                moduleNode.data.recommendations.forEach { recId ->
                    modules[recId]?.let { recNode ->
                        graph.addEdge(moduleNode, recNode)
                        // 发布软依赖添加事件
                        publishModuleDependencyChangeEvent(
                            moduleNode, recNode,
                            ModuleGraphEvent.DependencyType.SOFT,
                            ModuleGraphEvent.ChangeType.ADDED
                        )
                    }
                }
            } catch (e: IllegalArgumentException) {
                SparkCore.LOGGER.error("模块 ${moduleNode.id} 检测到循环依赖！", e)
                // 在这里可以添加错误处理逻辑，例如禁用该模块
            }
        }
        SparkCore.LOGGER.info("模块依赖图构建完成。")

        // 依赖图构建完成后，立即按拓扑顺序触发模块的注册流程
        orderedModule = getModulesInTopologicalOrder()
        SparkCore.LOGGER.info("模块拓扑排序完成，将按以下顺序注册: ${orderedModule.joinToString { it.id }}")

        // 发布模块图重建事件
        publishGraphRebuiltEvent()
    }

    /**
     * 获取拓扑排序后的模块列表。
     * 增加了基于权重的次要排序逻辑。
     *
     * @return 按依赖和权重顺序排序的模块列表。
     * @throws IllegalStateException 如果检测到循环依赖。
     */
    fun getModulesInTopologicalOrder(): List<ModuleNode> {
        return try {
            val iterator = TopologicalOrderIterator(graph)
            val topologicalOrder = iterator.asSequence().toList()
            topologicalOrder.sortedWith(compareBy { moduleWeights[it.id] ?: 1000 })
        } catch (e: Exception) {
            SparkCore.LOGGER.error("无法对模块进行拓扑排序，请检查是否存在循环依赖。", e)
            emptyList()
        }
    }

    /**
     * 设置模块的加载权重。
     * 权重值越小，优先级越高。
     *
     * @param moduleId 模块的唯一ID。
     * @param weight 模块的权重。
     */
    fun setModuleWeight(moduleId: String, weight: Int) {
        if (modules.containsKey(moduleId)) {
            moduleWeights[moduleId] = weight
            SparkCore.LOGGER.info("模块 $moduleId 的加载权重已设置为 $weight")
        } else {
            SparkCore.LOGGER.warn("尝试为不存在的模块 $moduleId 设置权重。")
        }
    }

    /**
     * 获取模块的权重。
     *
     * @param moduleId 模块的唯一ID。
     * @return 模块的权重，如果未设置则返回默认值1000。
     */
    fun getModuleWeight(moduleId: String): Int {
        return moduleWeights[moduleId] ?: 1000
    }

    /**
     * 获取所有模块的权重设置。
     *
     * @return 包含所有模块权重设置的Map。
     */
    fun getAllModuleWeights(): Map<String, Int> {
        return moduleWeights.toMap()
    }

    /**
     * 获取指定模块的节点。
     *
     * @param moduleId 模块的唯一ID。
     * @return ModuleNode? 模块节点，如果不存在则为null。
     */
    fun getModuleNode(moduleId: String): ModuleNode? {
        return modules[moduleId]
    }

    /**
     * 获取所有已注册的模块节点。
     *
     * @return 一个包含所有已注册模块ID到模块节点的映射的Map。
     */
    fun getAllModuleNodes(): Map<String, ModuleNode> {
        return modules.toMap()
    }

    /**
     * 添加动态模块依赖关系
     * 用于资源级依赖导致的模块间依赖
     *
     * @param sourceModuleId 源模块ID
     * @param targetModuleId 目标模块ID
     * @return 是否成功添加依赖
     */
    fun addDynamicDependency(sourceModuleId: String, targetModuleId: String): Boolean {
        val sourceModule = modules[sourceModuleId]
        val targetModule = modules[targetModuleId]

        if (sourceModule == null) {
            SparkCore.LOGGER.warn("源模块不存在，无法添加动态依赖: $sourceModuleId -> $targetModuleId")
            return false
        }

        if (targetModule == null) {
            SparkCore.LOGGER.warn("目标模块不存在，无法添加动态依赖: $sourceModuleId -> $targetModuleId")
            return false
        }

        // 检查是否已存在依赖关系
        if (graph.containsEdge(sourceModule, targetModule)) {
            SparkCore.LOGGER.debug("模块依赖关系已存在: $sourceModuleId -> $targetModuleId")
            return true
        }

        try {
            graph.addEdge(sourceModule, targetModule)
            SparkCore.LOGGER.debug("添加动态模块依赖: $sourceModuleId -> $targetModuleId")
            return true
        } catch (e: IllegalArgumentException) {
            SparkCore.LOGGER.error("添加动态模块依赖失败，可能导致循环依赖: $sourceModuleId -> $targetModuleId", e)
            return false
        }
    }

    /**
     * 移除动态模块依赖关系
     *
     * @param sourceModuleId 源模块ID
     * @param targetModuleId 目标模块ID
     * @return 是否成功移除依赖
     */
    fun removeDynamicDependency(sourceModuleId: String, targetModuleId: String): Boolean {
        val sourceModule = modules[sourceModuleId]
        val targetModule = modules[targetModuleId]

        if (sourceModule == null || targetModule == null) {
            return false
        }

        val edge = graph.getEdge(sourceModule, targetModule)
        if (edge != null) {
            graph.removeEdge(edge)
            SparkCore.LOGGER.debug("移除动态模块依赖: $sourceModuleId -> $targetModuleId")
            return true
        }

        return false
    }

    /**
     * 清理所有数据，用于重新加载等场景。
     */
    fun clear() {
        modules.clear()
        val verticesToRemove = graph.vertexSet().toSet()
        verticesToRemove.forEach { graph.removeVertex(it) }
        moduleWeights.clear()
        SparkCore.LOGGER.info("ModuleGraphManager 已被清理。")
    }

    // ==================== 事件发布机制 ====================

    /**
     * 发布模块变更事件
     * @param moduleNode 发生变更的模块节点
     * @param changeType 变更类型
     */
    private fun publishModuleChangeEvent(moduleNode: ModuleNode, changeType: ModuleGraphEvent.ChangeType) {
        try {
            val event = ModuleGraphEvent.NodeChange(moduleNode, changeType)
            NeoForge.EVENT_BUS.post(event)
            SparkCore.LOGGER.debug("发布模块变更事件: {} - {}", moduleNode.id, changeType)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("发布模块变更事件失败: ${moduleNode.id}", e)
        }
    }

    /**
     * 发布模块依赖关系变更事件
     * @param source 依赖源模块节点
     * @param target 依赖目标模块节点
     * @param dependencyType 依赖类型
     * @param changeType 变更类型
     */
    private fun publishModuleDependencyChangeEvent(
        source: ModuleNode,
        target: ModuleNode,
        dependencyType: ModuleGraphEvent.DependencyType,
        changeType: ModuleGraphEvent.ChangeType
    ) {
        try {
            val event = ModuleGraphEvent.DependencyChange(source, target, dependencyType, changeType)
            NeoForge.EVENT_BUS.post(event)
            SparkCore.LOGGER.debug(
                "发布模块依赖变更事件: {} -> {} ({}) - {}",
                source.id,
                target.id,
                dependencyType,
                changeType
            )
        } catch (e: Exception) {
            SparkCore.LOGGER.error("发布模块依赖变更事件失败: ${source.id} -> ${target.id}", e)
        }
    }

    /**
     * 发布模块加载顺序变更事件
     * @param affectedModules 受影响的模块列表
     */
    private fun publishLoadOrderChangeEvent(affectedModules: List<ModuleNode>) {
        try {
            val event = ModuleGraphEvent.LoadOrderChange(affectedModules)
            NeoForge.EVENT_BUS.post(event)
            SparkCore.LOGGER.debug("发布模块加载顺序变更事件，影响 ${affectedModules.size} 个模块")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("发布模块加载顺序变更事件失败", e)
        }
    }

    /**
     * 发布模块图重建事件
     */
    private fun publishGraphRebuiltEvent() {
        try {
            val event = ModuleGraphEvent.GraphRebuilt
            NeoForge.EVENT_BUS.post(event)
            SparkCore.LOGGER.info("发布模块图重建事件")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("发布模块图重建事件失败", e)
        }
    }

}