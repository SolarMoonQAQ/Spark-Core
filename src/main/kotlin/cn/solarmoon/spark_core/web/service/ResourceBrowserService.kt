package cn.solarmoon.spark_core.web.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.web.adapter.ResourceSearchFilters
import cn.solarmoon.spark_core.web.adapter.ResourceStatusInfo
import cn.solarmoon.spark_core.web.adapter.ResourceTreeNode
import cn.solarmoon.spark_core.web.adapter.WebApiAdapter
import cn.solarmoon.spark_core.web.dto.ApiResponse
import cn.solarmoon.spark_core.resource.graph.ResourceNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * 资源浏览服务
 * 
 * 提供资源树浏览、搜索、过滤等功能的业务逻辑处理
 * 作为Web API和WebApiAdapter之间的桥梁，处理请求参数验证、
 * 错误处理、异步操作等。
 */
object ResourceBrowserService {
    
    /**
     * 获取资源树
     * 
     * @param refresh 是否强制刷新缓存
     * @return API响应，包含资源树数据
     */
    suspend fun getResourceTree(refresh: Boolean = false): ApiResponse<ResourceTreeNode> {
        return withContext(Dispatchers.IO) {
            try {
                val resourceTree = WebApiAdapter.getResourceTree(refresh)
                ApiResponse.success(resourceTree, "资源树获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源树失败", e)
                ApiResponse.error("获取资源树失败: ${e.message}")
            }
        }
    }
    
    /**
     * 搜索资源
     * 
     * @param query 搜索关键词
     * @param namespace 命名空间过滤
     * @param modId 模组ID过滤
     * @param moduleName 模块名过滤
     * @param resourceType 资源类型过滤
     * @param tags 标签过滤
     * @return API响应，包含搜索结果
     */
    suspend fun searchResources(
        query: String,
        namespace: String = "",
        modId: String = "",
        moduleName: String = "",
        resourceType: String = "",
        tags: List<String> = emptyList()
    ): ApiResponse<List<ResourceSearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建过滤条件
                val filters = ResourceSearchFilters(
                    namespace = namespace,
                    modId = modId,
                    moduleName = moduleName,
                    resourceType = resourceType,
                    tags = tags
                )
                
                // 执行搜索
                val searchResults = WebApiAdapter.searchNodes(query, filters)
                
                // 转换为前端友好的格式
                val results = searchResults.map { node ->
                    val status = WebApiAdapter.getResourceStatus(node)
                    ResourceSearchResult(
                        id = node.id.toString(),
                        name = extractResourceName(node.id.path),
                        namespace = node.namespace,
                        modId = node.modId,
                        moduleName = node.moduleName,
                        resourceType = inferResourceType(node.id.path),
                        tags = node.tags,
                        status = status
                    )
                }
                
                ApiResponse.success(results, "搜索完成，找到 ${results.size} 个结果")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("搜索资源失败", e)
                ApiResponse.error("搜索资源失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取资源详情
     * 
     * @param resourceId 资源ID
     * @return API响应，包含资源详情
     */
    suspend fun getResourceDetails(resourceId: String): ApiResponse<ResourceDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val resourceLocation = net.minecraft.resources.ResourceLocation.parse(resourceId)
                val node = cn.solarmoon.spark_core.resource.graph.ResourceNodeManager.getNode(resourceLocation)
                
                if (node == null) {
                    return@withContext ApiResponse.error<ResourceDetails>("资源不存在: $resourceId")
                }
                
                // 获取资源状态
                val status = WebApiAdapter.getResourceStatus(node)
                
                // 获取依赖关系
                val dependencyGraph = WebApiAdapter.getDependencyGraph(resourceId, true, false)
                
                // 获取被依赖关系
                val dependents = cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
                    .getDirectDependents(resourceLocation)
                    .map { (depNode, edgeType) ->
                        DependentInfo(
                            id = depNode.id.toString(),
                            name = extractResourceName(depNode.id.path),
                            type = inferResourceType(depNode.id.path),
                            dependencyType = edgeType.toString()
                        )
                    }
                
                // 构建详情对象
                val details = ResourceDetails(
                    id = node.id.toString(),
                    name = extractResourceName(node.id.path),
                    namespace = node.namespace,
                    modId = node.modId,
                    moduleName = node.moduleName,
                    resourceType = inferResourceType(node.id.path),
                    tags = node.tags,
                    properties = node.properties,
                    status = status,
                    dependencyGraph = dependencyGraph,
                    dependents = dependents,
                    physicalPath = "${node.basePath}/${node.relativePath}"
                )
                
                ApiResponse.success(details, "资源详情获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源详情失败", e)
                ApiResponse.error("获取资源详情失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取资源状态
     * 
     * @param resourceId 资源ID
     * @return API响应，包含资源状态
     */
    suspend fun getResourceStatus(resourceId: String): ApiResponse<ResourceStatusInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val status = WebApiAdapter.getResourceStatus(resourceId)
                ApiResponse.success(status, "资源状态获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源状态失败", e)
                ApiResponse.error("获取资源状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取资源统计信息
     * 
     * @return API响应，包含资源统计信息
     */
    suspend fun getResourceStats(): ApiResponse<ResourceStats> {
        return withContext(Dispatchers.IO) {
            try {
                val allNodes = cn.solarmoon.spark_core.resource.graph.ResourceNodeManager.getAllNodeValues()
                
                // 按命名空间统计
                val namespaceStats = allNodes.groupBy { it.namespace }
                    .mapValues { it.value.size }
                
                // 按模组ID统计
                val modIdStats = allNodes.groupBy { it.modId }
                    .mapValues { it.value.size }
                
                // 按模块名统计
                val moduleStats = allNodes.groupBy { "${it.modId}:${it.moduleName}" }
                    .mapValues { it.value.size }
                
                // 按资源类型统计
                val typeStats = allNodes.groupBy { inferResourceType(it.id.path) }
                    .mapValues { it.value.size }
                
                // 冲突统计
                val conflictCount = cn.solarmoon.spark_core.resource.conflict.ResourceConflictManager
                    .getAllConflicts().size
                
                val stats = ResourceStats(
                    totalResources = allNodes.size,
                    namespaceStats = namespaceStats,
                    modIdStats = modIdStats,
                    moduleStats = moduleStats,
                    resourceTypeStats = typeStats,
                    conflictCount = conflictCount
                )
                
                ApiResponse.success(stats, "资源统计信息获取成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取资源统计信息失败", e)
                ApiResponse.error("获取资源统计信息失败: ${e.message}")
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
}

/**
 * 资源搜索结果
 */
data class ResourceSearchResult(
    val id: String,
    val name: String,
    val namespace: String,
    val modId: String,
    val moduleName: String,
    val resourceType: String,
    val tags: List<String>,
    val status: ResourceStatusInfo
)

/**
 * 资源详情
 */
data class ResourceDetails(
    val id: String,
    val name: String,
    val namespace: String,
    val modId: String,
    val moduleName: String,
    val resourceType: String,
    val tags: List<String>,
    val properties: Map<String, Any>,
    val status: ResourceStatusInfo,
    val dependencyGraph: cn.solarmoon.spark_core.web.adapter.DependencyGraphData,
    val dependents: List<DependentInfo>,
    val physicalPath: String
)

/**
 * 依赖者信息
 */
data class DependentInfo(
    val id: String,
    val name: String,
    val type: String,
    val dependencyType: String
)

/**
 * 资源统计信息
 */
data class ResourceStats(
    val totalResources: Int,
    val namespaceStats: Map<String, Int>,
    val modIdStats: Map<String, Int>,
    val moduleStats: Map<String, Int>,
    val resourceTypeStats: Map<String, Int>,
    val conflictCount: Int
)
