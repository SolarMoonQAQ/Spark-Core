package cn.solarmoon.spark_core.web.adapter

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.conflict.ResourceConflictManager
import cn.solarmoon.spark_core.resource.graph.EdgeType
import cn.solarmoon.spark_core.resource.graph.ModuleDependencySync
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import cn.solarmoon.spark_core.resource.graph.ResourceNodeManager
import cn.solarmoon.spark_core.resource.origin.ODependencyType
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import cn.solarmoon.spark_core.resource.packaging.PackagingTool
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Web API适配器
 * 
 * 为Web界面提供数据访问层，封装底层资源管理组件的操作，
 * 提供资源树构建、资源搜索、依赖操作等方法。
 */
object WebApiAdapter {
    
    // 资源树缓存，避免频繁重建
    private val resourceTreeCache = ConcurrentHashMap<String, ResourceTreeNode>()
    private var lastTreeUpdateTime = 0L
    private const val CACHE_TTL = 5000L // 缓存有效期5秒
    
    /**
     * 获取资源树结构
     * 按namespace->modId->moduleName->resourceType层次构建树
     * 
     * @param refresh 是否强制刷新缓存
     * @return 资源树根节点
     */
    fun getResourceTree(refresh: Boolean = false): ResourceTreeNode {
        val now = System.currentTimeMillis()
        val cacheKey = "resourceTree"
        
        // 检查缓存是否有效
        if (!refresh && 
            resourceTreeCache.containsKey(cacheKey) && 
            (now - lastTreeUpdateTime < CACHE_TTL)) {
            return resourceTreeCache[cacheKey]!!
        }
        
        // 重建资源树
        val rootNode = ResourceTreeNode(
            id = "root",
            name = "Resources",
            type = "root",
            children = mutableListOf()
        )
        
        try {
            // 获取所有资源节点
            val allNodes = ResourceNodeManager.getAllNodeValues()
            
            // 按命名空间分组
            val namespaceGroups = allNodes.groupBy { it.namespace }
            
            // 构建命名空间层
            namespaceGroups.forEach { (namespace, namespaceNodes) ->
                val namespaceNode = ResourceTreeNode(
                    id = "namespace:$namespace",
                    name = namespace,
                    type = "namespace",
                    children = mutableListOf()
                )
                
                // 按modId分组
                val modIdGroups = namespaceNodes.groupBy { it.modId }
                
                // 构建modId层
                modIdGroups.forEach { (modId, modIdNodes) ->
                    val modIdNode = ResourceTreeNode(
                        id = "modId:$modId",
                        name = modId,
                        type = "modId",
                        children = mutableListOf()
                    )
                    
                    // 按moduleName分组
                    val moduleGroups = modIdNodes.groupBy { it.moduleName }
                    
                    // 构建moduleName层
                    moduleGroups.forEach { (moduleName, moduleNodes) ->
                        val moduleNode = ResourceTreeNode(
                            id = "module:$modId:$moduleName",
                            name = moduleName,
                            type = "module",
                            children = mutableListOf()
                        )
                        
                        // 按资源类型分组
                        val typeGroups = moduleNodes.groupBy { inferResourceType(it.id.path) }
                        
                        // 构建资源类型层
                        typeGroups.forEach { (resourceType, typeNodes) ->
                            val typeNode = ResourceTreeNode(
                                id = "type:$modId:$moduleName:$resourceType",
                                name = resourceType,
                                type = "resourceType",
                                children = mutableListOf()
                            )
                            
                            // 添加资源节点
                            typeNodes.forEach { resourceNode ->
                                val resourceName = extractResourceName(resourceNode.id.path)
                                val resourceStatus = getResourceStatus(resourceNode)
                                
                                val leafNode = ResourceTreeNode(
                                    id = resourceNode.id.toString(),
                                    name = resourceName,
                                    type = "resource",
                                    resourceNode = resourceNode,
                                    status = resourceStatus,
                                    children = null
                                )
                                
                                typeNode.children?.add(leafNode)
                            }
                            
                            // 只添加非空的资源类型节点
                            if (!typeNode.children.isNullOrEmpty()) {
                                moduleNode.children?.add(typeNode)
                            }
                        }
                        
                        // 只添加非空的模块节点
                        if (!moduleNode.children.isNullOrEmpty()) {
                            modIdNode.children?.add(moduleNode)
                        }
                    }
                    
                    // 只添加非空的modId节点
                    if (!modIdNode.children.isNullOrEmpty()) {
                        namespaceNode.children?.add(modIdNode)
                    }
                }
                
                // 只添加非空的命名空间节点
                if (!namespaceNode.children.isNullOrEmpty()) {
                    rootNode.children?.add(namespaceNode)
                }
            }
            
            // 更新缓存
            resourceTreeCache[cacheKey] = rootNode
            lastTreeUpdateTime = now
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("构建资源树失败", e)
        }
        
        return rootNode
    }
    
    /**
     * 搜索资源节点
     * 
     * @param query 搜索关键词
     * @param filters 过滤条件
     * @return 匹配的资源节点列表
     */
    fun searchNodes(query: String, filters: ResourceSearchFilters): List<ResourceNode> {
        val allNodes = ResourceNodeManager.getAllNodeValues()
        
        return allNodes.filter { node ->
            // 应用关键词过滤
            val matchesQuery = query.isEmpty() || 
                node.id.toString().contains(query, ignoreCase = true) ||
                node.tags.any { it.contains(query, ignoreCase = true) }
            
            // 应用命名空间过滤
            val matchesNamespace = filters.namespace.isEmpty() || 
                node.namespace == filters.namespace
            
            // 应用modId过滤
            val matchesModId = filters.modId.isEmpty() || 
                node.modId == filters.modId
            
            // 应用模块名过滤
            val matchesModule = filters.moduleName.isEmpty() || 
                node.moduleName == filters.moduleName
            
            // 应用资源类型过滤
            val matchesType = filters.resourceType.isEmpty() || 
                inferResourceType(node.id.path) == filters.resourceType
            
            // 应用标签过滤
            val matchesTags = filters.tags.isEmpty() || 
                filters.tags.all { tag -> node.tags.contains(tag) }
            
            // 所有条件都满足
            matchesQuery && matchesNamespace && matchesModId && 
                matchesModule && matchesType && matchesTags
        }
    }
    
    /**
     * 获取资源状态信息
     * 
     * @param resourceId 资源ID
     * @return 资源状态信息
     */
    fun getResourceStatus(resourceId: String): ResourceStatusInfo {
        val resourceLocation = ResourceLocation.parse(resourceId)
        return getResourceStatus(ResourceNodeManager.getNode(resourceLocation))
    }
    
    /**
     * 获取资源状态信息
     * 
     * @param node 资源节点
     * @return 资源状态信息
     */
    fun getResourceStatus(node: ResourceNode?): ResourceStatusInfo {
        if (node == null) {
            return ResourceStatusInfo(
                exists = false,
                hasConflict = false,
                hasDependents = false,
                hasDependencies = false,
                isOverridden = false,
                isOverriding = false
            )
        }
        
        // 检查是否有冲突
        val hasConflict = ResourceConflictManager.getAllConflicts()
            .containsKey(node.id.toString())
        
        // 检查是否有依赖者
        val hasDependents = ResourceGraphManager.getDirectDependents(node.id).isNotEmpty()
        
        // 检查是否有依赖
        val hasDependencies = ResourceGraphManager.getGraph().outDegreeOf(node) > 0
        
        // 检查是否被覆盖
        val isOverridden = ResourceGraphManager.getOverridesFor(node.id).isNotEmpty()
        
        // 检查是否覆盖其他资源
        val isOverriding = ResourceGraphManager.getAllOverrides()
            .entries.any { it.key.id == node.id }
        
        return ResourceStatusInfo(
            exists = true,
            hasConflict = hasConflict,
            hasDependents = hasDependents,
            hasDependencies = hasDependencies,
            isOverridden = isOverridden,
            isOverriding = isOverriding
        )
    }
    
    /**
     * 添加依赖关系
     * 
     * @param sourceId 源资源ID
     * @param targetId 目标资源ID
     * @param dependencyType 依赖类型
     * @return 是否成功添加
     */
    fun addDependency(sourceId: String, targetId: String, dependencyType: String): Boolean {
        try {
            val sourceLocation = ResourceLocation.parse(sourceId)
            val targetLocation = ResourceLocation.parse(targetId)
            
            val sourceNode = ResourceNodeManager.getNode(sourceLocation)
            val targetNode = ResourceNodeManager.getNode(targetLocation)
            
            if (sourceNode == null || targetNode == null) {
                SparkCore.LOGGER.error("添加依赖失败：源节点或目标节点不存在")
                return false
            }
            
            // 创建依赖对象
            val dependency = OResourceDependency(
                id = targetLocation,
                type = when (dependencyType.uppercase()) {
                    "HARD" -> ODependencyType.HARD
                    "SOFT" -> ODependencyType.SOFT
                    else -> ODependencyType.OPTIONAL
                },
                path = targetLocation.path,
                extraProps = mapOf(
                    "manual_added" to true,
                    "added_time" to System.currentTimeMillis()
                )
            )
            
            // 获取当前依赖列表
            val graph = ResourceGraphManager.getGraph()
            val currentDependencies = graph.outgoingEdgesOf(sourceNode).mapNotNull { edge ->
                val target = graph.getEdgeTarget(edge)
                OResourceDependency(
                    id = target.id,
                    type = when (edge) {
                        EdgeType.HARD_DEPENDENCY -> ODependencyType.HARD
                        else -> ODependencyType.SOFT
                    },
                    path = target.id.path
                )
            }.toMutableList()
            
            // 添加新依赖
            if (!currentDependencies.any { it.id == targetLocation }) {
                currentDependencies.add(dependency)
                
                // 更新依赖关系
                val edgeType = when (dependency.type) {
                    ODependencyType.HARD -> EdgeType.HARD_DEPENDENCY
                    ODependencyType.SOFT, ODependencyType.OPTIONAL -> EdgeType.SOFT_DEPENDENCY
                }
                
                graph.addEdge(sourceNode, targetNode, edgeType)
                
                // 同步到模块依赖
                ModuleDependencySync.syncModuleDependencies(sourceNode, currentDependencies)
                
                // 同步到磁盘
                val moduleId = "${sourceNode.modId}:${sourceNode.moduleName}"
                ModuleDependencySync.syncModuleInfoToDisk(moduleId)
                
                return true
            }
            
            return false
        } catch (e: Exception) {
            SparkCore.LOGGER.error("添加依赖失败", e)
            return false
        }
    }
    
    /**
     * 移除依赖关系
     * 
     * @param sourceId 源资源ID
     * @param targetId 目标资源ID
     * @return 是否成功移除
     */
    fun removeDependency(sourceId: String, targetId: String): Boolean {
        try {
            val sourceLocation = ResourceLocation.parse(sourceId)
            val targetLocation = ResourceLocation.parse(targetId)
            
            val sourceNode = ResourceNodeManager.getNode(sourceLocation)
            val targetNode = ResourceNodeManager.getNode(targetLocation)
            
            if (sourceNode == null || targetNode == null) {
                SparkCore.LOGGER.error("移除依赖失败：源节点或目标节点不存在")
                return false
            }
            
            // 获取当前依赖列表
            val graph = ResourceGraphManager.getGraph()
            val currentDependencies = graph.outgoingEdgesOf(sourceNode).mapNotNull { edge ->
                val target = graph.getEdgeTarget(edge)
                if (target.id != targetLocation) {
                    OResourceDependency(
                        id = target.id,
                        type = when (edge) {
                            EdgeType.HARD_DEPENDENCY -> ODependencyType.HARD
                            else -> ODependencyType.SOFT
                        },
                        path = target.id.path
                    )
                } else null
            }.toMutableList()
            
            // 移除边
            val edgeToRemove = graph.getAllEdges(sourceNode, targetNode).firstOrNull()
            if (edgeToRemove != null) {
                graph.removeEdge(edgeToRemove)
                
                // 同步到模块依赖
                ModuleDependencySync.syncModuleDependencies(sourceNode, currentDependencies)
                
                // 同步到磁盘
                val moduleId = "${sourceNode.modId}:${sourceNode.moduleName}"
                ModuleDependencySync.syncModuleInfoToDisk(moduleId)
                
                return true
            }
            
            return false
        } catch (e: Exception) {
            SparkCore.LOGGER.error("移除依赖失败", e)
            return false
        }
    }
    
    /**
     * 验证依赖关系是否有效
     * 检查是否会形成循环依赖
     * 
     * @param sourceId 源资源ID
     * @param targetId 目标资源ID
     * @return 验证结果
     */
    fun validateDependency(sourceId: String, targetId: String): DependencyValidationResult {
        try {
            val sourceLocation = ResourceLocation.parse(sourceId)
            val targetLocation = ResourceLocation.parse(targetId)
            
            // 检查节点是否存在
            val sourceNode = ResourceNodeManager.getNode(sourceLocation)
            val targetNode = ResourceNodeManager.getNode(targetLocation)
            
            if (sourceNode == null) {
                return DependencyValidationResult(
                    isValid = false,
                    errorMessage = "源资源不存在: $sourceId"
                )
            }
            
            if (targetNode == null) {
                return DependencyValidationResult(
                    isValid = false,
                    errorMessage = "目标资源不存在: $targetId"
                )
            }
            
            // 检查是否自依赖
            if (sourceId == targetId) {
                return DependencyValidationResult(
                    isValid = false,
                    errorMessage = "资源不能依赖自身"
                )
            }
            
            // 检查是否会形成循环依赖
            val targetDependencies = ResourceGraphManager.getAllDependencies(targetLocation, false)
            if (targetDependencies.any { it.id == sourceLocation }) {
                return DependencyValidationResult(
                    isValid = false,
                    errorMessage = "添加此依赖会形成循环依赖"
                )
            }
            
            return DependencyValidationResult(
                isValid = true,
                errorMessage = null
            )
        } catch (e: Exception) {
            SparkCore.LOGGER.error("验证依赖失败", e)
            return DependencyValidationResult(
                isValid = false,
                errorMessage = "验证过程发生错误: ${e.message}"
            )
        }
    }
    
    /**
     * 获取资源的依赖关系图
     * 
     * @param resourceId 资源ID
     * @param includeIndirect 是否包含间接依赖
     * @param hardOnly 是否只包含硬依赖
     * @return 依赖关系图数据
     */
    fun getDependencyGraph(resourceId: String, includeIndirect: Boolean, hardOnly: Boolean): DependencyGraphData {
        try {
            val resourceLocation = ResourceLocation.parse(resourceId)
            val sourceNode = ResourceNodeManager.getNode(resourceLocation)
            
            if (sourceNode == null) {
                return DependencyGraphData(
                    nodes = emptyList(),
                    edges = emptyList()
                )
            }
            
            val nodes = mutableListOf<DependencyGraphNode>()
            val edges = mutableListOf<DependencyGraphEdge>()
            
            // 添加源节点
            nodes.add(
                DependencyGraphNode(
                    id = sourceNode.id.toString(),
                    name = extractResourceName(sourceNode.id.path),
                    type = inferResourceType(sourceNode.id.path),
                    status = getResourceStatus(sourceNode)
                )
            )
            
            // 获取直接依赖
            val graph = ResourceGraphManager.getGraph()
            val directDependencies = graph.outgoingEdgesOf(sourceNode)
                .filter { !hardOnly || it == EdgeType.HARD_DEPENDENCY }
                .map { edge ->
                    val targetNode = graph.getEdgeTarget(edge)
                    val edgeType = edge.toString()
                    
                    // 添加目标节点
                    nodes.add(
                        DependencyGraphNode(
                            id = targetNode.id.toString(),
                            name = extractResourceName(targetNode.id.path),
                            type = inferResourceType(targetNode.id.path),
                            status = getResourceStatus(targetNode)
                        )
                    )
                    
                    // 添加边
                    edges.add(
                        DependencyGraphEdge(
                            source = sourceNode.id.toString(),
                            target = targetNode.id.toString(),
                            type = edgeType
                        )
                    )
                    
                    targetNode
                }
            
            // 如果需要包含间接依赖
            if (includeIndirect) {
                val processedNodes = mutableSetOf<String>()
                processedNodes.add(sourceNode.id.toString())
                
                // 处理每个直接依赖的间接依赖
                directDependencies.forEach { targetNode ->
                    processIndirectDependencies(targetNode, processedNodes, nodes, edges, hardOnly)
                }
            }
            
            return DependencyGraphData(
                nodes = nodes.distinctBy { it.id },
                edges = edges.distinctBy { "${it.source}-${it.target}" }
            )
        } catch (e: Exception) {
            SparkCore.LOGGER.error("获取依赖图失败", e)
            return DependencyGraphData(
                nodes = emptyList(),
                edges = emptyList()
            )
        }
    }
    
    /**
     * 递归处理间接依赖
     */
    private fun processIndirectDependencies(
        node: ResourceNode,
        processedNodes: MutableSet<String>,
        nodes: MutableList<DependencyGraphNode>,
        edges: MutableList<DependencyGraphEdge>,
        hardOnly: Boolean
    ) {
        // 标记当前节点为已处理
        processedNodes.add(node.id.toString())
        
        // 获取当前节点的直接依赖
        val graph = ResourceGraphManager.getGraph()
        val directDependencies = graph.outgoingEdgesOf(node)
            .filter { !hardOnly || it == EdgeType.HARD_DEPENDENCY }
            .map { edge ->
                val targetNode = graph.getEdgeTarget(edge)
                val edgeType = edge.toString()
                
                // 如果目标节点尚未处理
                if (!processedNodes.contains(targetNode.id.toString())) {
                    // 添加目标节点
                    nodes.add(
                        DependencyGraphNode(
                            id = targetNode.id.toString(),
                            name = extractResourceName(targetNode.id.path),
                            type = inferResourceType(targetNode.id.path),
                            status = getResourceStatus(targetNode)
                        )
                    )
                }
                
                // 添加边
                edges.add(
                    DependencyGraphEdge(
                        source = node.id.toString(),
                        target = targetNode.id.toString(),
                        type = edgeType
                    )
                )
                
                targetNode
            }
        
        // 递归处理未处理过的依赖
        directDependencies.forEach { targetNode ->
            if (!processedNodes.contains(targetNode.id.toString())) {
                processIndirectDependencies(targetNode, processedNodes, nodes, edges, hardOnly)
            }
        }
    }
    
    /**
     * 从资源路径中推断资源类型
     */
    private fun inferResourceType(path: String): String {
        val parts = path.split("/")
        return if (parts.size > 1) {
            parts[1] // 四层结构中的resourceType
        } else {
            "unknown"
        }
    }
    
    /**
     * 从资源路径中提取资源名称
     */
    private fun extractResourceName(path: String): String {
        val parts = path.split("/")
        return if (parts.size > 2) {
            parts.drop(2).joinToString("/") // 去掉moduleName和resourceType
        } else {
            path
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        resourceTreeCache.clear()
        lastTreeUpdateTime = 0
        SparkCore.LOGGER.debug("WebApiAdapter缓存已清除")
    }
}

/**
 * 资源树节点
 */
data class ResourceTreeNode(
    val id: String,
    val name: String,
    val type: String,
    val children: MutableList<ResourceTreeNode>? = null,
    val resourceNode: ResourceNode? = null,
    val status: ResourceStatusInfo? = null
)

/**
 * 资源状态信息
 */
data class ResourceStatusInfo(
    val exists: Boolean,
    val hasConflict: Boolean,
    val hasDependents: Boolean,
    val hasDependencies: Boolean,
    val isOverridden: Boolean,
    val isOverriding: Boolean
)

/**
 * 资源搜索过滤条件
 */
data class ResourceSearchFilters(
    val namespace: String = "",
    val modId: String = "",
    val moduleName: String = "",
    val resourceType: String = "",
    val tags: List<String> = emptyList()
)

/**
 * 依赖验证结果
 */
data class DependencyValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)

/**
 * 依赖图数据
 */
data class DependencyGraphData(
    val nodes: List<DependencyGraphNode>,
    val edges: List<DependencyGraphEdge>
)

/**
 * 依赖图节点
 */
data class DependencyGraphNode(
    val id: String,
    val name: String,
    val type: String,
    val status: ResourceStatusInfo
)

/**
 * 依赖图边
 */
data class DependencyGraphEdge(
    val source: String,
    val target: String,
    val type: String
)
