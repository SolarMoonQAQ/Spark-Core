package cn.solarmoon.spark_core.web.dto

import cn.solarmoon.spark_core.resource.graph.ResourceNode
import com.google.gson.annotations.SerializedName

/**
 * 资源管理相关DTO
 * 
 * 定义资源管理相关的数据传输对象，用于前后端数据交互
 */

/**
 * 资源树节点
 * 
 * @property id 节点ID
 * @property name 节点名称
 * @property type 节点类型 (namespace, modId, module, resourceType, resource)
 * @property children 子节点列表
 * @property status 资源状态信息 (仅对resource类型有效)
 * @property metadata 元数据
 */
data class ResourceTreeNodeDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("children")
    val children: List<ResourceTreeNodeDto>? = null,
    
    @SerializedName("status")
    val status: ResourceStatusDto? = null,
    
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)

/**
 * 资源状态
 * 
 * @property exists 资源是否存在
 * @property hasConflict 是否有冲突
 * @property hasDependents 是否有依赖者
 * @property hasDependencies 是否有依赖
 * @property isOverridden 是否被覆盖
 * @property isOverriding 是否覆盖其他资源
 */
data class ResourceStatusDto(
    @SerializedName("exists")
    val exists: Boolean = false,
    
    @SerializedName("hasConflict")
    val hasConflict: Boolean = false,
    
    @SerializedName("hasDependents")
    val hasDependents: Boolean = false,
    
    @SerializedName("hasDependencies")
    val hasDependencies: Boolean = false,
    
    @SerializedName("isOverridden")
    val isOverridden: Boolean = false,
    
    @SerializedName("isOverriding")
    val isOverriding: Boolean = false
)

/**
 * 资源搜索请求
 * 
 * @property query 搜索关键词
 * @property filters 过滤条件
 * @property page 页码
 * @property pageSize 每页大小
 */
data class ResourceSearchRequestDto(
    @SerializedName("query")
    val query: String = "",
    
    @SerializedName("filters")
    val filters: ResourceFilterDto = ResourceFilterDto(),
    
    @SerializedName("page")
    val page: Int = 1,
    
    @SerializedName("pageSize")
    val pageSize: Int = 20
)

/**
 * 资源过滤条件
 * 
 * @property namespace 命名空间
 * @property modId 模组ID
 * @property moduleName 模块名称
 * @property resourceType 资源类型
 * @property tags 标签列表
 */
data class ResourceFilterDto(
    @SerializedName("namespace")
    val namespace: String = "",
    
    @SerializedName("modId")
    val modId: String = "",
    
    @SerializedName("moduleName")
    val moduleName: String = "",
    
    @SerializedName("resourceType")
    val resourceType: String = "",
    
    @SerializedName("tags")
    val tags: List<String> = emptyList()
)

/**
 * 资源搜索结果
 * 
 * @property id 资源ID
 * @property name 资源名称
 * @property namespace 命名空间
 * @property modId 模组ID
 * @property moduleName 模块名称
 * @property resourceType 资源类型
 * @property tags 标签列表
 * @property status 资源状态
 * @property path 资源路径
 */
data class ResourceSearchResultDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("namespace")
    val namespace: String,
    
    @SerializedName("modId")
    val modId: String,
    
    @SerializedName("moduleName")
    val moduleName: String,
    
    @SerializedName("resourceType")
    val resourceType: String,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
    
    @SerializedName("status")
    val status: ResourceStatusDto,
    
    @SerializedName("path")
    val path: String? = null
)

/**
 * 资源详情
 * 
 * @property id 资源ID
 * @property name 资源名称
 * @property namespace 命名空间
 * @property modId 模组ID
 * @property moduleName 模块名称
 * @property resourceType 资源类型
 * @property tags 标签列表
 * @property properties 属性
 * @property status 资源状态
 * @property physicalPath 物理路径
 * @property dependencyCount 依赖数量
 * @property dependentCount 被依赖数量
 * @property lastModified 最后修改时间
 */
data class ResourceDetailDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("namespace")
    val namespace: String,
    
    @SerializedName("modId")
    val modId: String,
    
    @SerializedName("moduleName")
    val moduleName: String,
    
    @SerializedName("resourceType")
    val resourceType: String,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
    
    @SerializedName("properties")
    val properties: Map<String, Any> = emptyMap(),
    
    @SerializedName("status")
    val status: ResourceStatusDto,
    
    @SerializedName("physicalPath")
    val physicalPath: String? = null,
    
    @SerializedName("dependencyCount")
    val dependencyCount: Int = 0,
    
    @SerializedName("dependentCount")
    val dependentCount: Int = 0,
    
    @SerializedName("lastModified")
    val lastModified: Long? = null
)

/**
 * 资源统计信息
 * 
 * @property totalResources 总资源数
 * @property namespaceStats 命名空间统计
 * @property modIdStats 模组ID统计
 * @property moduleStats 模块统计
 * @property resourceTypeStats 资源类型统计
 * @property conflictCount 冲突数量
 */
data class ResourceStatsDto(
    @SerializedName("totalResources")
    val totalResources: Int = 0,
    
    @SerializedName("namespaceStats")
    val namespaceStats: Map<String, Int> = emptyMap(),
    
    @SerializedName("modIdStats")
    val modIdStats: Map<String, Int> = emptyMap(),
    
    @SerializedName("moduleStats")
    val moduleStats: Map<String, Int> = emptyMap(),
    
    @SerializedName("resourceTypeStats")
    val resourceTypeStats: Map<String, Int> = emptyMap(),
    
    @SerializedName("conflictCount")
    val conflictCount: Int = 0
)

/**
 * 资源树请求
 * 
 * @property refresh 是否刷新缓存
 * @property expandedNodes 展开的节点列表
 */
data class ResourceTreeRequestDto(
    @SerializedName("refresh")
    val refresh: Boolean = false,
    
    @SerializedName("expandedNodes")
    val expandedNodes: List<String> = emptyList()
)

/**
 * 分页响应
 * 
 * @param T 数据类型
 * @property items 数据项
 * @property total 总数
 * @property page 当前页
 * @property pageSize 每页大小
 * @property totalPages 总页数
 */
data class PagedResponseDto<T>(
    @SerializedName("items")
    val items: List<T>,
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("page")
    val page: Int,
    
    @SerializedName("pageSize")
    val pageSize: Int,
    
    @SerializedName("totalPages")
    val totalPages: Int
) {
    companion object {
        /**
         * 创建分页响应
         * 
         * @param items 数据项
         * @param total 总数
         * @param page 当前页
         * @param pageSize 每页大小
         * @return 分页响应
         */
        fun <T> create(items: List<T>, total: Int, page: Int, pageSize: Int): PagedResponseDto<T> {
            val totalPages = if (total % pageSize == 0) total / pageSize else total / pageSize + 1
            return PagedResponseDto(items, total, page, pageSize, totalPages)
        }
    }
}
