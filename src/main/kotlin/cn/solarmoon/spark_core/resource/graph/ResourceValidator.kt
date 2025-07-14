package cn.solarmoon.spark_core.resource.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.origin.OResourceDependency
import cn.solarmoon.spark_core.resource.origin.ODependencyType
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 资源验证器
 * 
 * 专门负责资源和依赖的验证，遵循单一职责原则。
 * 从ResourceGraphManager中提取验证逻辑，提供统一的验证接口。
 */
object ResourceValidator {
    
    /**
     * 验证严重性级别
     */
    enum class ValidationSeverity {
        IGNORE,     // 忽略
        LOG,        // 仅记录日志
        WARN,       // 警告
        ERROR       // 错误
    }
    
    /**
     * 验证配置
     */
    data class ValidationConfig(
        val checkHardDependencies: Boolean = true,
        val checkSoftDependencies: Boolean = true,
        val checkOptionalDependencies: Boolean = false,
        val logLevel: ValidationSeverity = ValidationSeverity.LOG,
        val failOnHardDependencyMissing: Boolean = true,
        val failOnVersionMismatch: Boolean = false,
        val enableDetailedLogging: Boolean = false
    )
    
    /**
     * 验证结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val resource: ResourceLocation,
        val results: List<cn.solarmoon.spark_core.resource.graph.DependencyValidationResult>,
        val errors: List<String>,
        val warnings: List<String>,
        val summary: ValidationSummary
    )
    
    /**
     * 验证摘要
     */
    data class ValidationSummary(
        val totalDependencies: Int,
        val successCount: Int,
        val missingCount: Int,
        val versionMismatchCount: Int,
        val hardDependencyFailures: Int,
        val softDependencyFailures: Int,
        val optionalDependencyFailures: Int
    )
    
    /**
     * 验证缓存
     */
    private val validationCache = ConcurrentHashMap<String, ValidationResult>()
    
    /**
     * 验证资源的所有依赖
     * 
     * @param resourceLocation 资源位置
     * @param config 验证配置
     * @return 验证结果
     */
    fun validateResource(
        resourceLocation: ResourceLocation,
        config: ValidationConfig = ValidationConfig()
    ): ValidationResult {
        val cacheKey = generateCacheKey(resourceLocation, config)
        
        // 检查缓存
        validationCache[cacheKey]?.let { cached ->
            if (config.enableDetailedLogging) {
                SparkCore.LOGGER.debug("使用缓存的验证结果: $resourceLocation")
            }
            return cached
        }
        
        // 执行验证
        val result = performValidation(resourceLocation, config)
        
        // 缓存结果
        validationCache[cacheKey] = result
        
        return result
    }
    
    /**
     * 验证依赖列表
     *
     * @param dependencies 依赖列表
     * @return 依赖验证结果列表
     */
    fun validateDependencies(dependencies: List<OResourceDependency>): List<DependencyValidationResult> {
        return dependencies.map { dependency ->
            validateSingleDependency(dependency)
        }
    }

    /**
     * 验证单个依赖
     *
     * @param dependency 要验证的依赖
     * @return 验证结果
     */
    fun validateSingleDependency(dependency: OResourceDependency): DependencyValidationResult {
        return try {
            // 检查依赖ID是否有效
            if (dependency.id.toString().isEmpty()) {
                return cn.solarmoon.spark_core.resource.graph.DependencyValidationResult(false, "依赖ID不能为空")
            }

            // 检查依赖是否存在
            val exists = checkDependencyExists(dependency.id)
            if (!exists) {
                return when (dependency.type) {
                    ODependencyType.HARD -> cn.solarmoon.spark_core.resource.graph.DependencyValidationResult(false, "硬依赖不存在: ${dependency.id}")
                    ODependencyType.SOFT -> cn.solarmoon.spark_core.resource.graph.DependencyValidationResult(true, "软依赖不存在，但可以继续")
                    ODependencyType.OPTIONAL -> cn.solarmoon.spark_core.resource.graph.DependencyValidationResult(true, "可选依赖不存在")
                }
            }

            cn.solarmoon.spark_core.resource.graph.DependencyValidationResult(true, "依赖验证成功")
        } catch (e: Exception) {
            cn.solarmoon.spark_core.resource.graph.DependencyValidationResult(false, "依赖验证异常: ${e.message}")
        }
    }
    
    /**
     * 检查是否存在硬依赖失败
     * 
     * @param resourceLocation 资源位置
     * @return 是否存在硬依赖失败
     */
    fun hasHardDependencyFailures(resourceLocation: ResourceLocation): Boolean {
        val result = validateResource(resourceLocation, ValidationConfig(
            checkHardDependencies = true,
            checkSoftDependencies = false,
            checkOptionalDependencies = false
        ))
        return result.summary.hardDependencyFailures > 0
    }
    
    /**
     * 批量验证资源
     * 
     * @param resources 资源列表
     * @param config 验证配置
     * @return 验证结果列表
     */
    fun validateResourceBatch(
        resources: List<ResourceLocation>,
        config: ValidationConfig = ValidationConfig()
    ): List<ValidationResult> {
        return resources.map { resource ->
            validateResource(resource, config)
        }
    }
    
    /**
     * 生成验证报告
     * 
     * @param resourceLocation 资源位置
     * @return 验证报告字符串
     */
    fun generateValidationReport(resourceLocation: ResourceLocation): String {
        val result = validateResource(resourceLocation)
        val report = StringBuilder()
        
        report.appendLine("=== 资源验证报告 ===")
        report.appendLine("资源: $resourceLocation")
        report.appendLine("状态: ${if (result.isValid) "✓ 有效" else "✗ 无效"}")
        report.appendLine()
        
        report.appendLine("依赖摘要:")
        report.appendLine("  总依赖数: ${result.summary.totalDependencies}")
        report.appendLine("  成功: ${result.summary.successCount}")
        report.appendLine("  缺失: ${result.summary.missingCount}")
        report.appendLine("  硬依赖失败: ${result.summary.hardDependencyFailures}")
        report.appendLine("  软依赖失败: ${result.summary.softDependencyFailures}")
        report.appendLine()
        
        if (result.errors.isNotEmpty()) {
            report.appendLine("错误:")
            result.errors.forEach { error ->
                report.appendLine("  ✗ $error")
            }
            report.appendLine()
        }
        
        if (result.warnings.isNotEmpty()) {
            report.appendLine("警告:")
            result.warnings.forEach { warning ->
                report.appendLine("  ⚠ $warning")
            }
        }
        
        return report.toString()
    }
    
    /**
     * 获取验证摘要
     * 
     * @param resourceLocation 资源位置
     * @return 验证摘要
     */
    fun getValidationSummary(resourceLocation: ResourceLocation): ValidationSummary {
        return validateResource(resourceLocation).summary
    }
    
    /**
     * 检查依赖是否存在
     * 
     * @param resourceId 资源ID
     * @return 是否存在
     */
    private fun checkDependencyExists(resourceId: ResourceLocation): Boolean {
        // 检查资源节点是否存在于图中
        return ResourceNodeManager.containsNode(resourceId)
    }
    
    /**
     * 执行实际的验证逻辑
     */
    private fun performValidation(
        resourceLocation: ResourceLocation,
        config: ValidationConfig
    ): ValidationResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // 获取依赖信息
            val dependencies = ResourceGraphManager.getDirectDependencies(resourceLocation)
            
            // 分析结果
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val results = mutableListOf<DependencyValidationResult>()
            
            var successCount = 0
            var missingCount = 0
            var versionMismatchCount = 0
            var hardFailures = 0
            var softFailures = 0
            var optionalFailures = 0
            
            for (dependency in dependencies) {
                val result = validateSingleDependency(dependency)
                results.add(result)
                
                if (result.isValid) {
                    successCount++
                    if (config.enableDetailedLogging) {
                        SparkCore.LOGGER.debug("依赖验证成功: ${dependency.id}")
                    }
                } else {
                    missingCount++
                    when (dependency.type) {
                        ODependencyType.HARD -> {
                            hardFailures++
                            errors.add(result.errorMessage ?: "硬依赖验证失败")
                            if (config.logLevel == ValidationSeverity.ERROR) {
                                SparkCore.LOGGER.error("硬依赖验证失败: ${dependency.id}")
                            }
                        }
                        ODependencyType.SOFT -> {
                            softFailures++
                            warnings.add(result.errorMessage ?: "软依赖验证失败")
                            if (config.logLevel == ValidationSeverity.WARN) {
                                SparkCore.LOGGER.warn("软依赖验证失败: ${dependency.id}")
                            }
                        }
                        ODependencyType.OPTIONAL -> {
                            optionalFailures++
                            if (config.checkOptionalDependencies) {
                                warnings.add(result.errorMessage ?: "可选依赖验证失败")
                            }
                        }
                    }
                }
            }
            
            val summary = ValidationSummary(
                totalDependencies = dependencies.size,
                successCount = successCount,
                missingCount = missingCount,
                versionMismatchCount = versionMismatchCount,
                hardDependencyFailures = hardFailures,
                softDependencyFailures = softFailures,
                optionalDependencyFailures = optionalFailures
            )
            
            val isValid = if (config.failOnHardDependencyMissing) {
                hardFailures == 0
            } else {
                true
            }
            
            val endTime = System.currentTimeMillis()
            if (config.enableDetailedLogging) {
                SparkCore.LOGGER.debug("验证完成: $resourceLocation (耗时: ${endTime - startTime}ms)")
            }
            
            return ValidationResult(
                isValid = isValid,
                resource = resourceLocation,
                results = results,
                errors = errors,
                warnings = warnings,
                summary = summary
            )
            
        } catch (e: Exception) {
            SparkCore.LOGGER.error("验证过程中发生异常: $resourceLocation", e)
            return ValidationResult(
                isValid = false,
                resource = resourceLocation,
                results = emptyList(),
                errors = listOf("验证异常: ${e.message}"),
                warnings = emptyList(),
                summary = ValidationSummary(0, 0, 0, 0, 1, 0, 0)
            )
        }
    }
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(resourceLocation: ResourceLocation, config: ValidationConfig): String {
        return "${resourceLocation}:${config.hashCode()}"
    }
    
    /**
     * 清除验证缓存
     */
    fun clearValidationCache() {
        validationCache.clear()
        SparkCore.LOGGER.debug("ResourceValidator 缓存已清理")
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    fun getCacheStatistics(): Map<String, Any> {
        return mapOf(
            "cache_size" to validationCache.size,
            "cache_entries" to validationCache.keys.toList()
        )
    }
}
