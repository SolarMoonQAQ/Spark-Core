package cn.solarmoon.spark_core.util

/**
 * 延迟任务数据类
 * @param remainingTicks 剩余 tick 计数，每次 processTasks(phase) 调用时减1，归零时执行
 * @param task 要执行的任务
 */
data class DelayedTask(
    var remainingTicks: Int,
    val task: () -> Unit
)
