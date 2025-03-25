package cn.solarmoon.spark_core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskSubmitOffice {

    val taskMap: ConcurrentHashMap<String, Pair<PPhase, () -> Unit>>
    val immediateQueue: ConcurrentLinkedDeque<Pair<PPhase, () -> Unit>>

    fun submitDeduplicatedTask(key: String, phase: PPhase, task: () -> Unit) {
        taskMap[key] = phase to task
    }

    fun submitImmediateTask(phase: PPhase, task: () -> Unit) {
        immediateQueue.add(phase to task)
    }

    fun processTasks(phase: PPhase) {
        // 处理去重任务
        taskMap.values.forEach { task ->
            if (task.first == phase) task.second.invoke()
        }
        taskMap.clear()

        // 处理即时任务
        while (immediateQueue.isNotEmpty()) {
            immediateQueue.poll()?.let {
                if (it.first == phase) it.second.invoke()
            }
        }
    }

}