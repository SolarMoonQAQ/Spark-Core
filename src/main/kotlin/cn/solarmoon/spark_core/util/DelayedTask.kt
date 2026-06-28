package cn.solarmoon.spark_core.util

/**
 * 延迟任务数据类。
 * <p>
 * 使用绝对 tick 计数调度，而非 processTasks 调用次数。
 * {@code executeAtTick} 在提交时由 {@link TaskSubmitOffice#getTickCount()} + delayTicks 计算，
 * 由 {@link TaskSubmitOffice#processTasks(PPhase)} 在 {@code getTickCount() >= executeAtTick} 时执行。
 * <p>
 * 此设计不依赖 PPhase 语义，无论 PPhase.ALL / PRE / POST 均一致。
 *
 * @param executeAtTick 目标执行 tick（{@link TaskSubmitOffice#getTickCount()} 达到此值时执行）
 * @param task 要执行的任务
 */
data class DelayedTask(
    val executeAtTick: Int,
    val task: () -> Unit
)
