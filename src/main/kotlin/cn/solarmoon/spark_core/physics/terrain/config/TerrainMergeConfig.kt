package cn.solarmoon.spark_core.physics.terrain.config

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.terrain.merge.TerrainPerformanceMetrics

/**
 * 地形合并配置类
 * 用于配置地形碰撞块合并的各种参数
 */
data class TerrainMergeConfig(
    /**
     * 合并阈值
     * 控制两个方块被认为是可合并的相似度阈值
     * 值越高，要求越严格，合并越少，但精度更高
     */
    var mergeThreshold: Float = 0.98f, // 提高阈值，减少合并机会

    /**
     * 最小合并尺寸
     * 只有当找到至少这么多相似的方块时才会进行合并
     */
    var minMergeSize: Int = 2, // 最小只需要2个方块

    /**
     * 最大合并尺寸
     * 将区域划分为这个大小的网格，每个网格内的方块会尝试合并
     */
    var maxMergeSize: Int = 1, // 减小最大合并尺寸为1

    /**
     * 是否启用垂直合并
     * 如果为true，会尝试在垂直方向上合并方块
     */
    var enableVerticalMerge: Boolean = true, // 禁用垂直合并

    /**
     * 最大高度差
     * 当启用垂直合并时，只有高度差小于这个值的方块才会被合并
     */
    var maxHeightDifference: Int = 1, // 最小高度差

    /**
     * 最大碰撞体尺寸
     * 限制合并后的碰撞体在任何维度上的最大尺寸
     */
    var maxCollisionBodySize: Float = 16.0f, // 减小最大碰撞体尺寸

    /**
     * 是否启用智能合并
     * 如果为true，会根据性能指标动态调整合并参数
     */
    var enableSmartMerging: Boolean = true,
    
    /**
     * 碰撞体过期时间
     * 单位为tick，用于设置碰撞体的过期计时器
     * 当碰撞体不再需要时，会在这段时间后被移除
     */
    var bodyExpirationTime: Int = 600 // 默认30秒 (20 ticks/s * 30s)
) {
    /**
     * 根据性能指标调整合并参数
     */
    fun adjust(metrics: TerrainPerformanceMetrics) {
        if (!enableSmartMerging) return

        // 记录调整前的配置
        val originalThreshold = mergeThreshold
        val originalMaxSize = maxMergeSize
        val originalVerticalMerge = enableVerticalMerge

        // 根据碰撞检测时间调整
        if (metrics.averageCollisionTime > 10.0) {
            // 严重性能问题，大幅度减少合并
            mergeThreshold = (mergeThreshold + 0.05f).coerceAtMost(0.98f)
            maxMergeSize = (maxMergeSize - 1).coerceAtLeast(1)
            enableVerticalMerge = false
        } else if (metrics.averageCollisionTime > 5.0) {
            // 中等性能问题，适度减少合并
            mergeThreshold = (mergeThreshold + 0.02f).coerceAtMost(0.97f)
        }

        // 根据内存使用率调整
        if (metrics.memoryUsage > 0.9f) {
            // 内存使用率过高，减少合并以释放内存
            maxMergeSize = (maxMergeSize - 1).coerceAtLeast(1)
        }

        // 根据合并成功率调整
        if (metrics.mergeSuccessRate < 0.3f) {
            // 合并成功率过低，降低要求
            mergeThreshold = (mergeThreshold - 0.02f).coerceAtLeast(0.8f)
        }

        // 记录是否有调整
        if (originalThreshold != mergeThreshold || 
            originalMaxSize != maxMergeSize || 
            originalVerticalMerge != enableVerticalMerge) {
            SparkCore.LOGGER.info(
                "地形合并配置已调整: 阈值 $originalThreshold -> $mergeThreshold, " +
                "最大尺寸 $originalMaxSize -> $maxMergeSize, " +
                "垂直合并 $originalVerticalMerge -> $enableVerticalMerge"
            )
        }
    }
}