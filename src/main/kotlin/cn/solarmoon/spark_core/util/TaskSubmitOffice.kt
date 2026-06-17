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

    fun submitDeduplicatedTask(key: String, phase: PPhase, task: () -> Unit) {
        taskMap.getOrPut(phase) { ConcurrentHashMap() } [key] = task
    }

    fun submitImmediateTask(phase: PPhase = PPhase.ALL, task: () -> Unit) {
        immediateQueue.getOrPut(phase) { ConcurrentLinkedDeque() }.add(task)
    }

    /**
     * 提交一个去重延迟任务，将在 processTasks(phase) 被调用 [delayTicks] 次后执行
     * 同一 phase + key 会覆盖旧的延迟任务（去重，类似 submitDeduplicatedTask）
     * @param key 任务唯一标识，用于去重
     * @param phase 任务执行阶段
     * @param delayTicks 延迟 tick 数（即等待 processTasks(phase) 调用次数）
     * @param task 要执行的任务
     */
    fun submitDelayedTask(key: String, phase: PPhase, delayTicks: Int, task: () -> Unit) {
        delayedTaskMap.getOrPut(phase) { ConcurrentHashMap() } [key] = DelayedTask(delayTicks, task)
    }

    /**
     * 提交一个非去重延迟任务，每次调用均会新增一个独立延迟任务，互不覆盖
     * @param phase 任务执行阶段
     * @param delayTicks 延迟 tick 数（即等待 processTasks(phase) 调用次数）
     * @param task 要执行的任务
     */
    fun submitDelayedTask(phase: PPhase, delayTicks: Int, task: () -> Unit) {
        delayedTaskQueue.getOrPut(phase) { ConcurrentLinkedDeque() }.add(DelayedTask(delayTicks, task))
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

        // 处理去重延迟任务：倒计数，归零时执行并移除
        val delayedMap = delayedTaskMap[phase]
        if (delayedMap != null) {
            val iter = delayedMap.entries.iterator()
            while (iter.hasNext()) {
                val (_, delayedTask) = iter.next()
                delayedTask.remainingTicks--
                if (delayedTask.remainingTicks <= 0) {
                    iter.remove()
                    delayedTask.task.invoke()
                }
            }
        }

        // 处理非去重延迟任务：倒计数，归零时执行并移除
        val delayedQ = delayedTaskQueue[phase]
        if (delayedQ != null) {
            val iter = delayedQ.iterator()
            while (iter.hasNext()) {
                val delayedTask = iter.next()
                delayedTask.remainingTicks--
                if (delayedTask.remainingTicks <= 0) {
                    iter.remove()
                    delayedTask.task.invoke()
                }
            }
        }
    }

}
