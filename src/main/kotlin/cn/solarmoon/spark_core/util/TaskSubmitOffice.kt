package cn.solarmoon.spark_core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskSubmitOffice {

    val taskMap: ConcurrentHashMap<PPhase, ConcurrentHashMap<String, () -> Unit>>
    val immediateQueue: ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<() -> Unit>>

    fun submitDeduplicatedTask(key: String, phase: PPhase, task: () -> Unit) {
        taskMap.getOrPut(phase) { ConcurrentHashMap() } [key] = task
    }

    fun submitImmediateTask(phase: PPhase = PPhase.ALL, task: () -> Unit) {
        immediateQueue.getOrPut(phase) { ConcurrentLinkedDeque() }.add(task)
    }

    fun processTasks(phase: PPhase) {
        // 处理去重任务
        taskMap.remove(phase)?.forEach {
            it.value.invoke()
        }

        // 处理即时任务
        val iq = immediateQueue[phase] ?: return
        while (iq.isNotEmpty()) {
            iq.poll()?.invoke()
        }
    }

}