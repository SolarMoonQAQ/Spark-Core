package cn.solarmoon.spark_core.web.dto

import com.google.gson.annotations.SerializedName

/**
 * 冲突处理相关DTO
 * 
 * 定义冲突处理相关的数据传输对象，用于前后端数据交互
 */

/**
 * 冲突信息
 * 
 * @property resourceId 资源ID
 * @property resourceName 资源名称
 * @property namespace 命名空间
 * @property modId 模组ID
 * @property moduleName 模块名称
 * @property resourceType 资源类型
 * @property currentFile 当前文件路径
 * @property legacyFile Legacy文件路径
 * @property detectedTime 检测时间
 * @property status 冲突状态
 */
data class ConflictInfoDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("resourceName")
    val resourceName: String,
    
    @SerializedName("namespace")
    val namespace: String,
    
    @SerializedName("modId")
    val modId: String,
    
    @SerializedName("moduleName")
    val moduleName: String,
    
    @SerializedName("resourceType")
    val resourceType: String,
    
    @SerializedName("currentFile")
    val currentFile: String,
    
    @SerializedName("legacyFile")
    val legacyFile: String,
    
    @SerializedName("detectedTime")
    val detectedTime: Long,
    
    @SerializedName("status")
    val status: ConflictStatusDto = ConflictStatusDto.UNRESOLVED
)

/**
 * 冲突状态
 */
enum class ConflictStatusDto {
    @SerializedName("UNRESOLVED")
    UNRESOLVED,
    
    @SerializedName("RESOLVED_CURRENT")
    RESOLVED_CURRENT,
    
    @SerializedName("RESOLVED_LEGACY")
    RESOLVED_LEGACY,
    
    @SerializedName("RESOLVED_MANUAL")
    RESOLVED_MANUAL
}

/**
 * 文件差异响应
 * 
 * @property resourceId 资源ID
 * @property currentContent 当前内容
 * @property legacyContent Legacy内容
 * @property currentFile 当前文件路径
 * @property legacyFile Legacy文件路径
 * @property diffLines 差异行列表
 * @property fileType 文件类型
 */
data class FileDiffResponseDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("currentContent")
    val currentContent: String,
    
    @SerializedName("legacyContent")
    val legacyContent: String,
    
    @SerializedName("currentFile")
    val currentFile: String,
    
    @SerializedName("legacyFile")
    val legacyFile: String,
    
    @SerializedName("diffLines")
    val diffLines: List<DiffLineDto>,
    
    @SerializedName("fileType")
    val fileType: String? = null
)

/**
 * 差异行
 * 
 * @property lineNumber 行号
 * @property currentLine 当前行内容
 * @property legacyLine Legacy行内容
 * @property changeType 变更类型
 */
data class DiffLineDto(
    @SerializedName("lineNumber")
    val lineNumber: Int,
    
    @SerializedName("currentLine")
    val currentLine: String?,
    
    @SerializedName("legacyLine")
    val legacyLine: String?,
    
    @SerializedName("changeType")
    val changeType: String // UNCHANGED, ADDED, REMOVED, MODIFIED
)

/**
 * 冲突解决请求
 * 
 * @property resourceId 资源ID
 * @property resolution 解决方案
 * @property customContent 自定义内容
 */
data class ConflictResolutionRequestDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("resolution")
    val resolution: String, // USE_CURRENT, USE_LEGACY, USE_CUSTOM
    
    @SerializedName("customContent")
    val customContent: String? = null
)

/**
 * 冲突解决结果
 * 
 * @property resourceId 资源ID
 * @property resolution 解决方案
 * @property success 是否成功
 * @property message 消息
 */
data class ConflictResolutionResultDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("resolution")
    val resolution: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String
)

/**
 * 批量冲突解决请求
 * 
 * @property operations 操作列表
 */
data class BatchConflictResolutionRequestDto(
    @SerializedName("operations")
    val operations: List<ConflictResolutionOperationDto>
)

/**
 * 冲突解决操作
 * 
 * @property resourceId 资源ID
 * @property resolution 解决方案
 */
data class ConflictResolutionOperationDto(
    @SerializedName("resourceId")
    val resourceId: String,
    
    @SerializedName("resolution")
    val resolution: String // USE_CURRENT, USE_LEGACY
)

/**
 * 批量冲突解决结果
 * 
 * @property totalOperations 总操作数
 * @property successCount 成功数
 * @property failureCount 失败数
 * @property results 操作结果列表
 */
data class BatchConflictResolutionResultDto(
    @SerializedName("totalOperations")
    val totalOperations: Int,
    
    @SerializedName("successCount")
    val successCount: Int,
    
    @SerializedName("failureCount")
    val failureCount: Int,
    
    @SerializedName("results")
    val results: List<ConflictResolutionResultDto>
)

/**
 * 冲突统计信息
 * 
 * @property totalConflicts 总冲突数
 * @property moduleStats 模块统计
 * @property resourceTypeStats 资源类型统计
 * @property namespaceStats 命名空间统计
 * @property resolvedConflicts 已解决冲突数
 * @property unresolvedConflicts 未解决冲突数
 */
data class ConflictStatsDto(
    @SerializedName("totalConflicts")
    val totalConflicts: Int,
    
    @SerializedName("moduleStats")
    val moduleStats: Map<String, Int>,
    
    @SerializedName("resourceTypeStats")
    val resourceTypeStats: Map<String, Int>,
    
    @SerializedName("namespaceStats")
    val namespaceStats: Map<String, Int>,
    
    @SerializedName("resolvedConflicts")
    val resolvedConflicts: Int = 0,
    
    @SerializedName("unresolvedConflicts")
    val unresolvedConflicts: Int = totalConflicts - resolvedConflicts
)

/**
 * 冲突检测请求
 * 
 * @property moduleId 模块ID
 * @property resourceIds 资源ID列表
 * @property forceRecheck 是否强制重新检查
 */
data class ConflictDetectionRequestDto(
    @SerializedName("moduleId")
    val moduleId: String? = null,
    
    @SerializedName("resourceIds")
    val resourceIds: List<String> = emptyList(),
    
    @SerializedName("forceRecheck")
    val forceRecheck: Boolean = false
)

/**
 * 冲突检测结果
 * 
 * @property totalChecked 总检查数
 * @property conflictsFound 发现冲突数
 * @property conflicts 冲突列表
 */
data class ConflictDetectionResultDto(
    @SerializedName("totalChecked")
    val totalChecked: Int,
    
    @SerializedName("conflictsFound")
    val conflictsFound: Int,
    
    @SerializedName("conflicts")
    val conflicts: List<ConflictInfoDto>
)

/**
 * 冲突过滤请求
 * 
 * @property namespace 命名空间
 * @property modId 模组ID
 * @property moduleName 模块名称
 * @property resourceType 资源类型
 * @property status 冲突状态
 */
data class ConflictFilterRequestDto(
    @SerializedName("namespace")
    val namespace: String? = null,
    
    @SerializedName("modId")
    val modId: String? = null,
    
    @SerializedName("moduleName")
    val moduleName: String? = null,
    
    @SerializedName("resourceType")
    val resourceType: String? = null,
    
    @SerializedName("status")
    val status: ConflictStatusDto? = null
)

/**
 * Legacy文件夹清理请求
 * 
 * @property cleanResolved 是否清理已解决的冲突
 * @property cleanAll 是否清理所有Legacy文件
 */
data class LegacyFolderCleanupRequestDto(
    @SerializedName("cleanResolved")
    val cleanResolved: Boolean = true,
    
    @SerializedName("cleanAll")
    val cleanAll: Boolean = false
)

/**
 * Legacy文件夹清理结果
 * 
 * @property filesRemoved 移除文件数
 * @property spaceFreed 释放空间 (字节)
 * @property success 是否成功
 * @property message 消息
 */
data class LegacyFolderCleanupResultDto(
    @SerializedName("filesRemoved")
    val filesRemoved: Int,
    
    @SerializedName("spaceFreed")
    val spaceFreed: Long,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String
)
