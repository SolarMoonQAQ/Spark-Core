package cn.solarmoon.spark_core.web.dto

import com.google.gson.annotations.SerializedName

/**
 * 依赖编辑相关DTO
 * 
 * 定义依赖编辑相关的数据传输对象，用于前后端数据交互
 */

/**
 * 依赖图响应
 * 
 * @property nodes 节点列表
 * @property edges 边列表
 * @property metadata 元数据
 */
data class DependencyGraphResponseDto(
    @SerializedName("nodes")
    val nodes: List<DependencyNodeDto>,
    
    @SerializedName("edges")
    val edges: List<DependencyEdgeDto>,
    
    @SerializedName("metadata")
    val metadata: DependencyGraphMetadataDto? = null
)

/**
 * 依赖图节点
 * 
 * @property id 节点ID
 * @property name 节点名称
 * @property type 节点类型
 * @property status 节点状态
 * @property position 节点位置
 * @property metadata 元数据
 */
data class DependencyNodeDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("status")
    val status: ResourceStatusDto,
    
    @SerializedName("position")
    val position: NodePositionDto? = null,
    
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)

/**
 * 依赖图边
 * 
 * @property id 边ID
 * @property source 源节点ID
 * @property target 目标节点ID
 * @property type 依赖类型 (HARD, SOFT, OPTIONAL)
 * @property metadata 元数据
 */
data class DependencyEdgeDto(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("source")
    val source: String,
    
    @SerializedName("target")
    val target: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)

/**
 * 节点位置
 * 
 * @property x X坐标
 * @property y Y坐标
 */
data class NodePositionDto(
    @SerializedName("x")
    val x: Double,
    
    @SerializedName("y")
    val y: Double
)

/**
 * 依赖图元数据
 * 
 * @property totalNodes 总节点数
 * @property totalEdges 总边数
 * @property maxDepth 最大深度
 * @property hasCircularDependency 是否有循环依赖
 */
data class DependencyGraphMetadataDto(
    @SerializedName("totalNodes")
    val totalNodes: Int,
    
    @SerializedName("totalEdges")
    val totalEdges: Int,
    
    @SerializedName("maxDepth")
    val maxDepth: Int,
    
    @SerializedName("hasCircularDependency")
    val hasCircularDependency: Boolean
)

/**
 * 依赖添加请求
 * 
 * @property sourceId 源资源ID
 * @property targetId 目标资源ID
 * @property dependencyType 依赖类型
 * @property validateFirst 是否先验证
 */
data class DependencyAddRequestDto(
    @SerializedName("sourceId")
    val sourceId: String,
    
    @SerializedName("targetId")
    val targetId: String,
    
    @SerializedName("dependencyType")
    val dependencyType: String = "SOFT",
    
    @SerializedName("validateFirst")
    val validateFirst: Boolean = true
)

/**
 * 依赖移除请求
 * 
 * @property sourceId 源资源ID
 * @property targetId 目标资源ID
 */
data class DependencyRemoveRequestDto(
    @SerializedName("sourceId")
    val sourceId: String,
    
    @SerializedName("targetId")
    val targetId: String
)

/**
 * 依赖验证请求
 * 
 * @property sourceId 源资源ID
 * @property targetId 目标资源ID
 */
data class DependencyValidationRequestDto(
    @SerializedName("sourceId")
    val sourceId: String,
    
    @SerializedName("targetId")
    val targetId: String
)

/**
 * 依赖验证结果
 * 
 * @property isValid 是否有效
 * @property errorMessage 错误消息
 * @property warnings 警告列表
 */
data class DependencyValidationResultDto(
    @SerializedName("isValid")
    val isValid: Boolean,
    
    @SerializedName("errorMessage")
    val errorMessage: String? = null,
    
    @SerializedName("warnings")
    val warnings: List<String> = emptyList()
)

/**
 * 批量依赖操作请求
 * 
 * @property operations 操作列表
 */
data class BatchDependencyRequestDto(
    @SerializedName("operations")
    val operations: List<DependencyOperationDto>
)

/**
 * 依赖操作
 * 
 * @property type 操作类型 (ADD, REMOVE)
 * @property sourceId 源资源ID
 * @property targetId 目标资源ID
 * @property dependencyType 依赖类型
 */
data class DependencyOperationDto(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("sourceId")
    val sourceId: String,
    
    @SerializedName("targetId")
    val targetId: String,
    
    @SerializedName("dependencyType")
    val dependencyType: String? = null
)

/**
 * 依赖操作结果
 * 
 * @property success 是否成功
 * @property sourceId 源资源ID
 * @property targetId 目标资源ID
 * @property operation 操作类型
 * @property dependencyType 依赖类型
 * @property message 消息
 */
data class DependencyOperationResultDto(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("sourceId")
    val sourceId: String,
    
    @SerializedName("targetId")
    val targetId: String,
    
    @SerializedName("operation")
    val operation: String,
    
    @SerializedName("dependencyType")
    val dependencyType: String? = null,
    
    @SerializedName("message")
    val message: String
)

/**
 * 批量依赖操作结果
 * 
 * @property totalOperations 总操作数
 * @property successCount 成功数
 * @property failureCount 失败数
 * @property results 操作结果列表
 */
data class BatchDependencyResultDto(
    @SerializedName("totalOperations")
    val totalOperations: Int,
    
    @SerializedName("successCount")
    val successCount: Int,
    
    @SerializedName("failureCount")
    val failureCount: Int,
    
    @SerializedName("results")
    val results: List<DependencyOperationResultDto>
)

/**
 * 依赖建议
 * 
 * @property targetId 目标资源ID
 * @property targetName 目标资源名称
 * @property dependencyType 建议的依赖类型
 * @property reason 建议原因
 * @property confidence 置信度 (0.0 - 1.0)
 */
data class DependencySuggestionDto(
    @SerializedName("targetId")
    val targetId: String,
    
    @SerializedName("targetName")
    val targetName: String,
    
    @SerializedName("dependencyType")
    val dependencyType: String,
    
    @SerializedName("reason")
    val reason: String,
    
    @SerializedName("confidence")
    val confidence: Double
)

/**
 * 依赖图查询请求
 * 
 * @property resourceId 资源ID
 * @property includeIndirect 是否包含间接依赖
 * @property hardOnly 是否只包含硬依赖
 * @property maxDepth 最大深度
 */
data class DependencyGraphQueryDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("includeIndirect")
    val includeIndirect: Boolean = true,
    
    @SerializedName("hardOnly")
    val hardOnly: Boolean = false,
    
    @SerializedName("maxDepth")
    val maxDepth: Int = -1
)

/**
 * 依赖分析结果
 * 
 * @property resourceId 资源ID
 * @property directDependencies 直接依赖数
 * @property indirectDependencies 间接依赖数
 * @property directDependents 直接被依赖数
 * @property indirectDependents 间接被依赖数
 * @property circularDependencies 循环依赖列表
 */
data class DependencyAnalysisDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("directDependencies")
    val directDependencies: Int,
    
    @SerializedName("indirectDependencies")
    val indirectDependencies: Int,
    
    @SerializedName("directDependents")
    val directDependents: Int,
    
    @SerializedName("indirectDependents")
    val indirectDependents: Int,
    
    @SerializedName("circularDependencies")
    val circularDependencies: List<String> = emptyList()
)
