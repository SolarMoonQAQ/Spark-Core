package cn.solarmoon.spark_core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskSubmitOffice {

    val taskMap: ConcurrentHashMap<String, () -> Unit>
    val immediateQueue: ConcurrentLinkedDeque<() -> Unit>

    fun submitDeduplicatedTask(key: String, task: () -> Unit) {
        taskMap[key] = task
    }

    fun submitImmediateTask(task: () -> Unit) {
        immediateQueue.add(task)
    }

    fun processTasks() {
        // 处理去重任务
        taskMap.values.forEach { task ->
            task.invoke()
        }
        taskMap.clear()

        // 处理即时任务
        while (immediateQueue.isNotEmpty()) {
            immediateQueue.poll()?.invoke()
        }
    }

}