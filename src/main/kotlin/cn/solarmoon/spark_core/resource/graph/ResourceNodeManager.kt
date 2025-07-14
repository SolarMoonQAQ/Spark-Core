package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 资源节点管理器
 * 
 * 专门负责资源节点的CRUD操作，遵循单一职责原则。
 * 提供线程安全的资源节点存储和管理功能。
 */
object ResourceNodeManager {
    
    private val resources = ConcurrentHashMap<ResourceLocation, ResourceNode>()
    
    /**
     * 添加一个新的资源节点
     * 
     * @param node 要添加的资源节点
     * @return 是否成功添加（如果节点已存在则返回false）
     */
    fun addNode(node: ResourceNode): Boolean {
        val existing = resources.putIfAbsent(node.id, node)
        if (existing == null) {
            SparkCore.LOGGER.debug("资源节点已添加: ${node.id}")
            return true
        } else {
            SparkCore.LOGGER.debug("资源节点已存在，添加失败: ${node.id}")
            return false
        }
    }
    
    /**
     * 更新一个现有的资源节点
     * 
     * @param node 要更新的资源节点
     * @return 是否成功更新（如果节点不存在则返回false）
     */
    fun updateNode(node: ResourceNode): Boolean {
        val existing = resources.replace(node.id, node)
        if (existing != null) {
            SparkCore.LOGGER.debug("资源节点已更新: ${node.id}")
            return true
        } else {
            SparkCore.LOGGER.debug("资源节点不存在，更新失败: ${node.id}")
            return false
        }
    }
    
    /**
     * 添加或更新资源节点
     * 如果节点不存在则添加，如果存在则更新
     * 
     * @param node 要添加或更新的资源节点
     * @return 操作前的节点（如果是新添加则返回null）
     */
    fun addOrUpdateNode(node: ResourceNode): ResourceNode? {
        val previous = resources.put(node.id, node)
        if (previous == null) {
            SparkCore.LOGGER.debug("资源节点已添加: ${node.id}")
        } else {
            SparkCore.LOGGER.debug("资源节点已更新: ${node.id}")
        }
        return previous
    }
    
    /**
     * 移除一个资源节点
     * 
     * @param resourceId 要移除的资源ID
     * @return 被移除的节点，如果节点不存在则返回null
     */
    fun removeNode(resourceId: ResourceLocation): ResourceNode? {
        val removed = resources.remove(resourceId)
        if (removed != null) {
            SparkCore.LOGGER.debug("资源节点已移除: $resourceId")
        } else {
            SparkCore.LOGGER.debug("资源节点不存在，移除失败: $resourceId")
        }
        return removed
    }
    
    /**
     * 获取一个资源节点
     * 
     * @param resourceId 资源ID
     * @return 对应的资源节点，如果不存在则返回null
     */
    fun getNode(resourceId: ResourceLocation): ResourceNode? {
        return resources[resourceId]
    }
    
    /**
     * 检查资源节点是否存在
     * 
     * @param resourceId 资源ID
     * @return 是否存在
     */
    fun containsNode(resourceId: ResourceLocation): Boolean {
        return resources.containsKey(resourceId)
    }
    
    /**
     * 获取所有资源节点
     * 
     * @return 所有资源节点的只读映射
     */
    fun getAllNodes(): Map<ResourceLocation, ResourceNode> {
        return resources.toMap()
    }
    
    /**
     * 获取所有资源节点的集合
     * 
     * @return 所有资源节点的集合
     */
    fun getAllNodeValues(): Collection<ResourceNode> {
        return resources.values
    }
    
    /**
     * 获取所有资源ID
     * 
     * @return 所有资源ID的集合
     */
    fun getAllResourceIds(): Set<ResourceLocation> {
        return resources.keys.toSet()
    }
    
    /**
     * 获取资源节点数量
     * 
     * @return 当前存储的资源节点数量
     */
    fun getNodeCount(): Int {
        return resources.size
    }
    
    /**
     * 清空所有资源节点
     */
    fun clear() {
        val count = resources.size
        resources.clear()
        SparkCore.LOGGER.info("ResourceNodeManager 已清理，移除了 $count 个资源节点")
    }
    
    /**
     * 批量添加资源节点
     * 
     * @param nodes 要添加的资源节点列表
     * @return 成功添加的节点数量
     */
    fun addNodes(nodes: Collection<ResourceNode>): Int {
        var addedCount = 0
        nodes.forEach { node ->
            if (addNode(node)) {
                addedCount++
            }
        }
        SparkCore.LOGGER.debug("批量添加资源节点完成: $addedCount/${nodes.size}")
        return addedCount
    }
    
    /**
     * 批量移除资源节点
     * 
     * @param resourceIds 要移除的资源ID列表
     * @return 成功移除的节点数量
     */
    fun removeNodes(resourceIds: Collection<ResourceLocation>): Int {
        var removedCount = 0
        resourceIds.forEach { resourceId ->
            if (removeNode(resourceId) != null) {
                removedCount++
            }
        }
        SparkCore.LOGGER.debug("批量移除资源节点完成: $removedCount/${resourceIds.size}")
        return removedCount
    }
}
