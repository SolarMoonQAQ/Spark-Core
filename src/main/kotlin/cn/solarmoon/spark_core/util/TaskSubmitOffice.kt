package cn.solarmoon.spark_core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskSubmitOffice {

    val taskMap: ConcurrentHashMap<PPhase, ConcurrentHashMap<String, () -> Unit>>
    val immediateQueue: ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<() -> Unit>>
    /** 去重延迟任务映射：phase -> (key -> 延迟任务) */
    val delayedTaskMap: ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>>
    /** 非去重延迟任务队列：phase -> 延迟任务队列，每次调用均新增 */
    val delayedTaskQueue: ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>>

    /**
     * 获取当前 tick 计数（单调递增）。
     * <p>
     * 用于延迟任务调度：提交时记录 {@code getTickCount() + delayTicks}，
     * {@link #processTasks} 在 {@code getTickCount() >= executeAtTick} 时执行。
     * <p>
     * 实现者应提供与游戏逻辑 tick 相关的计数：
     * <ul>
     *   <li>{@link cn.solarmoon.spark_core.physics.level.PhysicsLevel} — 物理 tick 数</li>
     *   <li>{@code ServerLevel}（通过 {@code LevelMixin}）— {@code Level#getGameTime()}</li>
     * </ul>
     */
    val tickCount: Int

    fun submitDeduplicatedTask(key: String, phase: PPhase, task: () -> Unit) {
        taskMap.getOrPut(phase) { ConcurrentHashMap() } [key] = task
    }

    fun submitImmediateTask(phase: PPhase = PPhase.ALL, task: () -> Unit) {
        immediateQueue.getOrPut(phase) { ConcurrentLinkedDeque() }.add(task)
    }

    /**
     * 提交一个去重延迟任务，将在 [delayTicks] tick 后执行。
     * 同一 phase + key 会覆盖旧的延迟任务（去重，类似 submitDeduplicatedTask）。
     * <p>
     * 使用绝对 tick 计数（{@link #getTickCount()} + delayTicks），
     * 不依赖 PPhase 语义——无论使用 ALL / PRE / POST 均一致。
     *
     * @param key 任务唯一标识，用于去重
     * @param phase 任务执行阶段
     * @param delayTicks 延迟 tick 数（0 = 立即，1 = 至少一次 processTasks 后）
     * @param task 要执行的任务
     */
    fun submitDelayedTask(key: String, phase: PPhase, delayTicks: Int, task: () -> Unit) {
        delayedTaskMap.getOrPut(phase) { ConcurrentHashMap() } [key] =
            DelayedTask(tickCount + delayTicks, task)
    }

    /**
     * 提交一个非去重延迟任务，每次调用均会新增一个独立延迟任务，互不覆盖。
     *
     * @param phase 任务执行阶段
     * @param delayTicks 延迟 tick 数（0 = 立即，1 = 至少一次 processTasks 后）
     * @param task 要执行的任务
     */
    fun submitDelayedTask(phase: PPhase, delayTicks: Int, task: () -> Unit) {
        delayedTaskQueue.getOrPut(phase) { ConcurrentLinkedDeque() }.add(
            DelayedTask(tickCount + delayTicks, task)
        )
    }

    fun processTasks(phase: PPhase) {
        // 处理去重任务
        taskMap.remove(phase)?.forEach {
            it.value.invoke()
        }

        // 处理即时任务
        val iq = immediateQueue[phase]
        if (iq != null) {
            while (iq.isNotEmpty()) {
                iq.poll()?.invoke()
            }
        }

        val now = tickCount

        // 处理去重延迟任务：tick 计数达到目标值时执行
        val delayedMap = delayedTaskMap[phase]
        if (delayedMap != null) {
            val iter = delayedMap.entries.iterator()
            while (iter.hasNext()) {
                val (_, delayedTask) = iter.next()
                if (now >= delayedTask.executeAtTick) {
                    iter.remove()
                    delayedTask.task.invoke()
                }
            }
        }

        // 处理非去重延迟任务
        val delayedQ = delayedTaskQueue[phase]
        if (delayedQ != null) {
            val iter = delayedQ.iterator()
            while (iter.hasNext()) {
                val delayedTask = iter.next()
                if (now >= delayedTask.executeAtTick) {
                    iter.remove()
                    delayedTask.task.invoke()
                }
            }
        }
    }

}
