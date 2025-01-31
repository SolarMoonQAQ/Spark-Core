package cn.solarmoon.spark_core.physics.level

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

interface TaskSubmitOffice {

    // 使用 Pair<Key, Task> 结构，Key用于去重，Task为实际逻辑
    val taskQueue: ConcurrentLinkedDeque<Pair<String, () -> Unit>>
    val pendingKeys: ConcurrentHashMap<String, Boolean> // 去重标记

    /**
     * 提交可去重任务（如实体位置同步）
     */
    fun submitDeduplicatedTask(key: String, task: () -> Unit) {
        if (pendingKeys.putIfAbsent(key, true) == null) {
            taskQueue.add(key to task)
        } else {
            // 替换为最新任务（保留最后一个）
            taskQueue.removeIf { it.first == key }
            taskQueue.add(key to task)
        }
    }

    /**
     * 提交即时任务（如粒子效果生成）
     */
    fun submitImmediateTask(task: () -> Unit) {
        taskQueue.add("immediate_${System.nanoTime()}" to task)
    }

    /**
     * 处理任务进程
     */
    fun processTasks() {
        while (taskQueue.isNotEmpty()) {
            val (key, task) = taskQueue.poll() ?: return
            task.invoke()
            pendingKeys.remove(key)
        }
    }

}