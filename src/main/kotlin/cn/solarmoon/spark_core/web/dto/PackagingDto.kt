package cn.solarmoon.spark_core.web.dto

import com.google.gson.annotations.SerializedName

/**
 * 打包管理相关DTO
 * 
 * 定义打包管理相关的数据传输对象，用于前后端数据交互
 */

/**
 * 打包配置
 * 
 * @property resourceIds 资源ID列表
 * @property moduleIds 模块ID列表
 * @property includeDependencies 是否包含依赖
 * @property includeSoftDependencies 是否包含软依赖
 * @property outputPath 输出路径
 * @property packageName 包名
 * @property includeMetadata 是否包含元数据
 * @property compressionLevel 压缩级别 (0-9)
 * @property executeImmediately 是否立即执行
 */
data class PackagingConfigDto(
    @SerializedName("resourceIds")
    val resourceIds: List<String> = emptyList(),
    
    @SerializedName("moduleIds")
    val moduleIds: List<String> = emptyList(),
    
    @SerializedName("includeDependencies")
    val includeDependencies: Boolean = true,
    
    @SerializedName("includeSoftDependencies")
    val includeSoftDependencies: Boolean = false,
    
    @SerializedName("outputPath")
    val outputPath: String = "",
    
    @SerializedName("packageName")
    val packageName: String = "",
    
    @SerializedName("includeMetadata")
    val includeMetadata: Boolean = true,
    
    @SerializedName("compressionLevel")
    val compressionLevel: Int = 9,
    
    @SerializedName("executeImmediately")
    val executeImmediately: Boolean = true
)

/**
 * 打包任务状态
 */
enum class PackagingTaskStatusDto {
    @SerializedName("PENDING")
    PENDING,
    
    @SerializedName("RUNNING")
    RUNNING,
    
    @SerializedName("COMPLETED")
    COMPLETED,
    
    @SerializedName("FAILED")
    FAILED,
    
    @SerializedName("CANCELED")
    CANCELED
}

/**
 * 打包任务
 * 
 * @property id 任务ID
 * @property config 打包配置
 * @property status 任务状态
 * @property progress 进度 (0-100)
 * @property message 消息
 * @property createdTime 创建时间
 * @property startTime 开始时间
 * @property endTime 结束时间
 * @property estimatedTimeRemaining 预计剩余时间 (秒)
 */
data class PackagingTaskDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("config")
    val config: PackagingConfigDto,
    
    @SerializedName("status")
    val status: PackagingTaskStatusDto,
    
    @SerializedName("progress")
    val progress: Int,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("createdTime")
    val createdTime: Long,
    
    @SerializedName("startTime")
    val startTime: Long? = null,
    
    @SerializedName("endTime")
    val endTime: Long? = null,
    
    @SerializedName("estimatedTimeRemaining")
    val estimatedTimeRemaining: Int? = null
)

/**
 * 打包结果
 * 
 * @property taskId 任务ID
 * @property config 打包配置
 * @property resourceCount 资源数量
 * @property outputPath 输出路径
 * @property success 是否成功
 * @property message 消息
 * @property startTime 开始时间
 * @property endTime 结束时间
 * @property duration 持续时间 (毫秒)
 * @property fileSize 文件大小 (字节)
 */
data class PackagingResultDto(
    @SerializedName("taskId")
    val taskId: String,
    
    @SerializedName("config")
    val config: PackagingConfigDto,
    
    @SerializedName("resourceCount")
    val resourceCount: Int,
    
    @SerializedName("outputPath")
    val outputPath: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("startTime")
    val startTime: Long,
    
    @SerializedName("endTime")
    val endTime: Long,
    
    @SerializedName("duration")
    val duration: Long = endTime - startTime,
    
    @SerializedName("fileSize")
    val fileSize: Long? = null
)

/**
 * 打包任务创建请求
 * 
 * @property config 打包配置
 */
data class PackagingTaskCreateRequestDto(
    @SerializedName("config")
    val config: PackagingConfigDto
)

/**
 * 打包任务创建响应
 * 
 * @property taskId 任务ID
 */
data class PackagingTaskCreateResponseDto(
    @SerializedName("taskId")
    val taskId: String
)

/**
 * 打包任务状态响应
 * 
 * @property taskId 任务ID
 * @property status 任务状态
 * @property progress 进度 (0-100)
 * @property message 消息
 */
data class PackagingTaskStatusResponseDto(
    @SerializedName("taskId")
    val taskId: String,
    
    @SerializedName("status")
    val status: PackagingTaskStatusDto,
    
    @SerializedName("progress")
    val progress: Int,
    
    @SerializedName("message")
    val message: String
)

/**
 * 打包统计信息
 * 
 * @property totalTasks 总任务数
 * @property runningTasks 运行中任务数
 * @property completedTasks 已完成任务数
 * @property failedTasks 失败任务数
 * @property canceledTasks 已取消任务数
 * @property pendingTasks 等待中任务数
 * @property historyCount 历史记录数
 * @property autoPackagingEnabled 是否启用自动打包
 */
data class PackagingStatsDto(
    @SerializedName("totalTasks")
    val totalTasks: Int,
    
    @SerializedName("runningTasks")
    val runningTasks: Int,
    
    @SerializedName("completedTasks")
    val completedTasks: Int,
    
    @SerializedName("failedTasks")
    val failedTasks: Int,
    
    @SerializedName("canceledTasks")
    val canceledTasks: Int,
    
    @SerializedName("pendingTasks")
    val pendingTasks: Int,
    
    @SerializedName("historyCount")
    val historyCount: Int,
    
    @SerializedName("autoPackagingEnabled")
    val autoPackagingEnabled: Boolean
)

/**
 * 打包预设
 * 
 * @property id 预设ID
 * @property name 预设名称
 * @property description 预设描述
 * @property config 打包配置
 * @property isDefault 是否默认
 * @property createdTime 创建时间
 * @property lastUsedTime 最后使用时间
 */
data class PackagingPresetDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("config")
    val config: PackagingConfigDto,
    
    @SerializedName("isDefault")
    val isDefault: Boolean = false,
    
    @SerializedName("createdTime")
    val createdTime: Long,
    
    @SerializedName("lastUsedTime")
    val lastUsedTime: Long? = null
)

/**
 * 打包预设创建请求
 * 
 * @property name 预设名称
 * @property description 预设描述
 * @property config 打包配置
 * @property isDefault 是否默认
 */
data class PackagingPresetCreateRequestDto(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("config")
    val config: PackagingConfigDto,
    
    @SerializedName("isDefault")
    val isDefault: Boolean = false
)

/**
 * 打包资源分析
 * 
 * @property totalResources 总资源数
 * @property directResources 直接资源数
 * @property dependencyResources 依赖资源数
 * @property moduleBreakdown 模块分布
 * @property typeBreakdown 类型分布
 * @property estimatedSize 预计大小 (字节)
 */
data class PackagingResourceAnalysisDto(
    @SerializedName("totalResources")
    val totalResources: Int,
    
    @SerializedName("directResources")
    val directResources: Int,
    
    @SerializedName("dependencyResources")
    val dependencyResources: Int,
    
    @SerializedName("moduleBreakdown")
    val moduleBreakdown: Map<String, Int>,
    
    @SerializedName("typeBreakdown")
    val typeBreakdown: Map<String, Int>,
    
    @SerializedName("estimatedSize")
    val estimatedSize: Long
)

/**
 * 自动打包配置
 * 
 * @property enabled 是否启用
 * @property triggerOnResourceChange 资源变更时触发
 * @property triggerOnDependencyChange 依赖变更时触发
 * @property triggerOnConflictResolved 冲突解决时触发
 * @property defaultPresetId 默认预设ID
 */
data class AutoPackagingConfigDto(
    @SerializedName("enabled")
    val enabled: Boolean = false,
    
    @SerializedName("triggerOnResourceChange")
    val triggerOnResourceChange: Boolean = true,
    
    @SerializedName("triggerOnDependencyChange")
    val triggerOnDependencyChange: Boolean = true,
    
    @SerializedName("triggerOnConflictResolved")
    val triggerOnConflictResolved: Boolean = true,
    
    @SerializedName("defaultPresetId")
    val defaultPresetId: String? = null
)
