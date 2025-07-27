package cn.solarmoon.spark_core.web.service

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.conflict.ResourceConflictManager
import cn.solarmoon.spark_core.resource.graph.ResourceGraphManager
import cn.solarmoon.spark_core.resource.graph.ResourceNodeManager
import cn.solarmoon.spark_core.resource.packaging.PackagingTool
import cn.solarmoon.spark_core.web.dto.ApiResponse
import cn.solarmoon.spark_core.web.websocket.WebSocketEventBroadcaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.resources.ResourceLocation
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 打包管理服务
 * 
 * 提供打包配置、任务管理、进度监控功能，支持批量选择资源、配置预设、实时进度显示、结果管理等。
 * 替代AutoPackagingService，提供手动打包控制。
 */
object PackagingManagementService {
    
    // 打包任务线程池
    private val packagingExecutor = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "Packaging-Worker").apply { isDaemon = true }
    }
    
    // 打包任务状态
    private val packagingTasks = ConcurrentHashMap<String, PackagingTask>()
    
    // 打包任务历史
    private val packagingHistory = ConcurrentHashMap<String, PackagingResult>()
    
    // 最大历史记录数
    private const val MAX_HISTORY_SIZE = 50
    
    // 是否启用自动打包
    private val isAutoPackagingEnabled = AtomicBoolean(false)
    
    /**
     * 初始化打包管理服务
     */
    fun initialize() {
        try {
            SparkCore.LOGGER.info("打包管理服务已初始化")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("初始化打包管理服务失败", e)
        }
    }
    
    /**
     * 关闭打包管理服务
     */
    fun shutdown() {
        try {
            // 取消所有正在执行的任务
            packagingTasks.values.forEach { task ->
                if (task.status == PackagingTaskStatus.RUNNING) {
                    task.future?.cancel(true)
                }
            }
            
            // 关闭线程池
            packagingExecutor.shutdown()
            try {
                if (!packagingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    packagingExecutor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                packagingExecutor.shutdownNow()
            }
            
            SparkCore.LOGGER.info("打包管理服务已关闭")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("关闭打包管理服务失败", e)
        }
    }
    
    /**
     * 创建打包任务
     * 
     * @param config 打包配置
     * @return API响应，包含任务ID
     */
    suspend fun createPackagingTask(config: PackagingConfig): ApiResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 验证配置
                if (config.resourceIds.isEmpty() && config.moduleIds.isEmpty()) {
                    return@withContext ApiResponse.error("打包配置必须包含至少一个资源ID或模块ID")
                }
                
                // 创建任务ID
                val taskId = "pkg_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
                
                // 创建任务
                val task = PackagingTask(
                    id = taskId,
                    config = config,
                    status = PackagingTaskStatus.PENDING,
                    progress = 0,
                    message = "任务已创建，等待执行",
                    createdTime = System.currentTimeMillis(),
                    startTime = null,
                    endTime = null,
                    future = null
                )
                
                // 保存任务
                packagingTasks[taskId] = task
                
                // 如果是立即执行，则启动任务
                if (config.executeImmediately) {
                    executePackagingTask(taskId)
                }
                
                ApiResponse.success(taskId, "打包任务已创建")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("创建打包任务失败", e)
                ApiResponse.error("创建打包任务失败: ${e.message}")
            }
        }
    }
    
    /**
     * 执行打包任务
     * 
     * @param taskId 任务ID
     * @return API响应，包含执行结果
     */
    suspend fun executePackagingTask(taskId: String): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val task = packagingTasks[taskId]
                if (task == null) {
                    return@withContext ApiResponse.error("任务不存在: $taskId")
                }
                
                if (task.status == PackagingTaskStatus.RUNNING) {
                    return@withContext ApiResponse.error("任务已在执行中: $taskId")
                }
                
                // 更新任务状态
                val updatedTask = task.copy(
                    status = PackagingTaskStatus.RUNNING,
                    progress = 0,
                    message = "任务开始执行",
                    startTime = System.currentTimeMillis()
                )
                packagingTasks[taskId] = updatedTask
                
                // 广播任务开始事件
                WebSocketEventBroadcaster.broadcastPackagingProgress(
                    moduleId = task.config.moduleIds.firstOrNull() ?: "unknown",
                    progress = 0,
                    status = "STARTED",
                    message = "打包任务开始执行"
                )
                
                // 执行打包任务
                val future = CompletableFuture.supplyAsync({
                    try {
                        // 收集要打包的资源
                        val resourcesForPackaging = collectResources(task.config)
                        
                        if (resourcesForPackaging.isEmpty()) {
                            throw IllegalArgumentException("没有找到符合条件的资源")
                        }
                        
                        // 更新进度
                        updateTaskProgress(taskId, 10, "已收集 ${resourcesForPackaging.size} 个资源")

                        // 更新进度
                        updateTaskProgress(taskId, 20, "开始执行打包")

                        // 执行打包
                        val outputPath = executePackaging(task.config, resourcesForPackaging, taskId)
                        
                        // 更新进度
                        updateTaskProgress(taskId, 100, "打包完成: $outputPath")
                        
                        // 创建结果
                        val result = PackagingResult(
                            taskId = taskId,
                            config = task.config,
                            resourceCount = resourcesForPackaging.size,
                            outputPath = outputPath.toString(),
                            success = true,
                            message = "打包成功",
                            startTime = task.startTime ?: System.currentTimeMillis(),
                            endTime = System.currentTimeMillis()
                        )
                        
                        // 保存结果
                        packagingHistory[taskId] = result
                        
                        // 清理历史记录
                        cleanupHistory()
                        
                        // 更新任务状态
                        val completedTask = packagingTasks[taskId]?.copy(
                            status = PackagingTaskStatus.COMPLETED,
                            progress = 100,
                            message = "打包完成: $outputPath",
                            endTime = System.currentTimeMillis()
                        )
                        if (completedTask != null) {
                            packagingTasks[taskId] = completedTask
                        }
                        
                        // 广播任务完成事件
                        WebSocketEventBroadcaster.broadcastPackagingProgress(
                            moduleId = task.config.moduleIds.firstOrNull() ?: "unknown",
                            progress = 100,
                            status = "COMPLETED",
                            message = "打包任务完成"
                        )
                        
                        result
                    } catch (e: Exception) {
                        SparkCore.LOGGER.error("执行打包任务失败: $taskId", e)
                        
                        // 更新任务状态
                        val failedTask = packagingTasks[taskId]?.copy(
                            status = PackagingTaskStatus.FAILED,
                            progress = 0,
                            message = "打包失败: ${e.message}",
                            endTime = System.currentTimeMillis()
                        )
                        if (failedTask != null) {
                            packagingTasks[taskId] = failedTask
                        }
                        
                        // 广播任务失败事件
                        WebSocketEventBroadcaster.broadcastPackagingProgress(
                            moduleId = task.config.moduleIds.firstOrNull() ?: "unknown",
                            progress = 0,
                            status = "FAILED",
                            message = "打包任务失败: ${e.message}"
                        )
                        
                        // 创建失败结果
                        val result = PackagingResult(
                            taskId = taskId,
                            config = task.config,
                            resourceCount = 0,
                            outputPath = "",
                            success = false,
                            message = "打包失败: ${e.message}",
                            startTime = task.startTime ?: System.currentTimeMillis(),
                            endTime = System.currentTimeMillis()
                        )
                        
                        // 保存结果
                        packagingHistory[taskId] = result
                        
                        result
                    }
                }, packagingExecutor)
                
                // 更新任务
                packagingTasks[taskId] = updatedTask.copy(future = future)
                
                ApiResponse.success(true, "打包任务已开始执行")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("执行打包任务失败", e)
                ApiResponse.error("执行打包任务失败: ${e.message}")
            }
        }
    }
    
    /**
     * 取消打包任务
     * 
     * @param taskId 任务ID
     * @return API响应，包含取消结果
     */
    suspend fun cancelPackagingTask(taskId: String): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val task = packagingTasks[taskId]
                if (task == null) {
                    return@withContext ApiResponse.error("任务不存在: $taskId")
                }
                
                if (task.status != PackagingTaskStatus.RUNNING) {
                    return@withContext ApiResponse.error("任务不在执行中: $taskId")
                }
                
                // 取消任务
                task.future?.cancel(true)
                
                // 更新任务状态
                val canceledTask = task.copy(
                    status = PackagingTaskStatus.CANCELED,
                    message = "任务已取消",
                    endTime = System.currentTimeMillis()
                )
                packagingTasks[taskId] = canceledTask
                
                // 广播任务取消事件
                WebSocketEventBroadcaster.broadcastPackagingProgress(
                    moduleId = task.config.moduleIds.firstOrNull() ?: "unknown",
                    progress = 0,
                    status = "CANCELED",
                    message = "打包任务已取消"
                )
                
                ApiResponse.success(true, "打包任务已取消")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("取消打包任务失败", e)
                ApiResponse.error("取消打包任务失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取打包任务状态
     * 
     * @param taskId 任务ID
     * @return API响应，包含任务状态
     */
    suspend fun getPackagingTaskStatus(taskId: String): ApiResponse<PackagingTaskStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val task = packagingTasks[taskId]
                if (task == null) {
                    return@withContext ApiResponse.error("任务不存在: $taskId")
                }
                
                ApiResponse.success(task.status, "获取任务状态成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务状态失败", e)
                ApiResponse.error("获取打包任务状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取打包任务详情
     * 
     * @param taskId 任务ID
     * @return API响应，包含任务详情
     */
    suspend fun getPackagingTaskDetails(taskId: String): ApiResponse<PackagingTask> {
        return withContext(Dispatchers.IO) {
            try {
                val task = packagingTasks[taskId]
                if (task == null) {
                    return@withContext ApiResponse.error("任务不存在: $taskId")
                }
                
                ApiResponse.success(task, "获取任务详情成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务详情失败", e)
                ApiResponse.error("获取打包任务详情失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取打包任务列表
     * 
     * @param status 任务状态过滤
     * @return API响应，包含任务列表
     */
    suspend fun getPackagingTasks(status: String? = null): ApiResponse<List<PackagingTask>> {
        return withContext(Dispatchers.IO) {
            try {
                val tasks = if (status == null) {
                    packagingTasks.values.toList()
                } else {
                    packagingTasks.values.filter { it.status.name == status }.toList()
                }
                
                ApiResponse.success(tasks, "获取任务列表成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务列表失败", e)
                ApiResponse.error("获取打包任务列表失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取打包任务历史
     * 
     * @param limit 限制数量
     * @return API响应，包含任务历史
     */
    suspend fun getPackagingHistory(limit: Int = 10): ApiResponse<List<PackagingResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val history = packagingHistory.values
                    .sortedByDescending { it.endTime }
                    .take(limit)
                
                ApiResponse.success(history, "获取任务历史成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包任务历史失败", e)
                ApiResponse.error("获取打包任务历史失败: ${e.message}")
            }
        }
    }
    
    /**
     * 设置自动打包启用状态
     * 
     * @param enabled 是否启用
     * @return API响应，包含设置结果
     */
    suspend fun setAutoPackagingEnabled(enabled: Boolean): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                isAutoPackagingEnabled.set(enabled)
                ApiResponse.success(enabled, "自动打包${if (enabled) "已启用" else "已禁用"}")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("设置自动打包状态失败", e)
                ApiResponse.error("设置自动打包状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取自动打包启用状态
     * 
     * @return API响应，包含启用状态
     */
    suspend fun getAutoPackagingEnabled(): ApiResponse<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                ApiResponse.success(isAutoPackagingEnabled.get(), "获取自动打包状态成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取自动打包状态失败", e)
                ApiResponse.error("获取自动打包状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取打包统计信息
     * 
     * @return API响应，包含统计信息
     */
    suspend fun getPackagingStats(): ApiResponse<PackagingStats> {
        return withContext(Dispatchers.IO) {
            try {
                val stats = PackagingStats(
                    totalTasks = packagingTasks.size,
                    runningTasks = packagingTasks.values.count { it.status == PackagingTaskStatus.RUNNING },
                    completedTasks = packagingTasks.values.count { it.status == PackagingTaskStatus.COMPLETED },
                    failedTasks = packagingTasks.values.count { it.status == PackagingTaskStatus.FAILED },
                    canceledTasks = packagingTasks.values.count { it.status == PackagingTaskStatus.CANCELED },
                    pendingTasks = packagingTasks.values.count { it.status == PackagingTaskStatus.PENDING },
                    historyCount = packagingHistory.size,
                    autoPackagingEnabled = isAutoPackagingEnabled.get()
                )
                
                ApiResponse.success(stats, "获取打包统计信息成功")
            } catch (e: Exception) {
                SparkCore.LOGGER.error("获取打包统计信息失败", e)
                ApiResponse.error("获取打包统计信息失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新任务进度
     */
    private fun updateTaskProgress(taskId: String, progress: Int, message: String) {
        try {
            val task = packagingTasks[taskId] ?: return
            
            // 更新任务状态
            val updatedTask = task.copy(
                progress = progress,
                message = message
            )
            packagingTasks[taskId] = updatedTask
            
            // 广播进度更新事件
            WebSocketEventBroadcaster.broadcastPackagingProgress(
                moduleId = task.config.moduleIds.firstOrNull() ?: "unknown",
                progress = progress,
                status = "RUNNING",
                message = message
            )
        } catch (e: Exception) {
            SparkCore.LOGGER.error("更新任务进度失败", e)
        }
    }
    
    /**
     * 收集要打包的资源
     */
    private fun collectResources(config: PackagingConfig): List<ResourceLocation> {
        val resources = mutableListOf<ResourceLocation>()
        
        // 添加指定的资源
        config.resourceIds.forEach { resourceId ->
            try {
                val resourceLocation = ResourceLocation.parse(resourceId)
                if (ResourceNodeManager.containsNode(resourceLocation)) {
                    resources.add(resourceLocation)
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.warn("无效的资源ID: $resourceId", e)
            }
        }
        
        // 添加指定模块的所有资源
        config.moduleIds.forEach { moduleId ->
            val moduleParts = moduleId.split(":")
            if (moduleParts.size == 2) {
                val modId = moduleParts[0]
                val moduleName = moduleParts[1]
                
                ResourceNodeManager.getAllNodeValues()
                    .filter { it.modId == modId && it.moduleName == moduleName }
                    .forEach { resources.add(it.id) }
            }
        }
        
        // 如果包含依赖，添加所有依赖资源
        if (config.includeDependencies) {
            val dependencyResources = mutableListOf<ResourceLocation>()
            resources.forEach { resourceId ->
                val dependencies = ResourceGraphManager.getAllDependencies(resourceId, !config.includeSoftDependencies)
                dependencies.forEach { dependencyResources.add(it.id) }
            }
            resources.addAll(dependencyResources)
        }
        
        // 去重
        return resources.distinct()
    }
    
    /**
     * 执行打包
     */
    private fun executePackaging(config: PackagingConfig, resources: List<ResourceLocation>, taskId: String): Path {
        val progressTracker = AtomicInteger(20)

        // 创建进度回调
        val progressCallback = { progress: Int, message: String ->
            val adjustedProgress = 20 + (progress * 0.8).toInt() // 20% - 100%
            updateTaskProgress(taskId, adjustedProgress, message)
        }

        // 根据配置选择打包方法
        val outputPath = if (config.outputPath.isNotEmpty()) {
            java.nio.file.Paths.get(config.outputPath)
        } else {
            // 默认输出路径
            val gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
            val outputDir = gameDir.resolve("sparkcore").resolve("packages")
            java.nio.file.Files.createDirectories(outputDir)
            outputDir.resolve("${config.packageName.ifEmpty { "package_${System.currentTimeMillis()}" }}.spark")
        }

        // 使用PackagingTool的bakeAndPackage方法
        val result = PackagingTool.bakeAndPackage(
            newModuleId = config.packageName.ifEmpty { "custom_package" },
            newModuleVersion = "1.0.0",
            selectedResources = resources,
            dependencyScopes = emptyMap(), // 使用默认的依赖范围
            outputPath = outputPath
        )

        // 处理打包结果
        return when (result) {
            is cn.solarmoon.spark_core.resource.packaging.PackagingTool.PackagingResult.Success -> {
                progressCallback(100, "打包成功，包含 ${result.fileCount} 个文件")
                result.path
            }
            is cn.solarmoon.spark_core.resource.packaging.PackagingTool.PackagingResult.Error -> {
                throw RuntimeException("打包失败: ${result.message}")
            }
        }
    }
    
    /**
     * 清理历史记录
     */
    private fun cleanupHistory() {
        if (packagingHistory.size > MAX_HISTORY_SIZE) {
            val sortedHistory = packagingHistory.values
                .sortedByDescending { it.endTime }
                .drop(MAX_HISTORY_SIZE)
                .map { it.taskId }
            
            sortedHistory.forEach { packagingHistory.remove(it) }
        }
    }
}

/**
 * 打包配置
 */
data class PackagingConfig(
    val resourceIds: List<String> = emptyList(),
    val moduleIds: List<String> = emptyList(),
    val includeDependencies: Boolean = true,
    val includeSoftDependencies: Boolean = false,
    val outputPath: String = "",
    val packageName: String = "",
    val includeMetadata: Boolean = true,
    val compressionLevel: Int = 9,
    val executeImmediately: Boolean = true
)

/**
 * 打包任务
 */
data class PackagingTask(
    val id: String,
    val config: PackagingConfig,
    val status: PackagingTaskStatus,
    val progress: Int,
    val message: String,
    val createdTime: Long,
    val startTime: Long?,
    val endTime: Long?,
    val future: CompletableFuture<PackagingResult>?
)

/**
 * 打包任务状态
 */
enum class PackagingTaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}

/**
 * 打包结果
 */
data class PackagingResult(
    val taskId: String,
    val config: PackagingConfig,
    val resourceCount: Int,
    val outputPath: String,
    val success: Boolean,
    val message: String,
    val startTime: Long,
    val endTime: Long
)

/**
 * 打包统计信息
 */
data class PackagingStats(
    val totalTasks: Int,
    val runningTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val canceledTasks: Int,
    val pendingTasks: Int,
    val historyCount: Int,
    val autoPackagingEnabled: Boolean
)
