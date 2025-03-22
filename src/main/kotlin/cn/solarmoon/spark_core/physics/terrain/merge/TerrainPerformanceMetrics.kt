package cn.solarmoon.spark_core.physics.terrain.merge

/**
 * 地形性能指标收集类
 * 用于收集和分析地形系统的性能数据
 */
data class TerrainPerformanceMetrics(
    /**
     * 平均碰撞检测时间(ms)
     */
    var averageCollisionTime: Double = 0.0,

    /**
     * 内存使用率(0-1)
     */
    var memoryUsage: Float = 0.0f,

    /**
     * 合并后的碰撞体数量
     */
    var mergedBodyCount: Int = 0,

    /**
     * 原始碰撞体数量
     */
    var originalBodyCount: Int = 0,

    /**
     * 合并成功率(0-1)
     */
    var mergeSuccessRate: Float = 0.0f,

    /**
     * 平均合并时间(ms)
     */
    var averageMergeTime: Double = 0.0
) {
    /**
     * 计算合并效率
     * 返回值范围0-1，越大表示合并效果越好
     */
    fun calculateMergeEfficiency(): Float {
        if (originalBodyCount == 0) return 0f
        return 1f - (mergedBodyCount.toFloat() / originalBodyCount)
    }

    /**
     * 判断是否需要优化
     */
    fun needsOptimization(): Boolean {
        return averageCollisionTime > 5.0 || 
               memoryUsage > 0.8f || 
               mergeSuccessRate < 0.5f
    }
} 