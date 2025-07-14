package cn.solarmoon.spark_core.resource

import cn.solarmoon.spark_core.SparkCore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 资源提取完成追踪器
 * 使用事件驱动机制确保热重载服务在资源提取完成后启动
 */
object ResourceExtractionCompletionTracker {
    
    private val isComplete = AtomicBoolean(false)
    private val expectedHandlers = AtomicInteger(0)
    private val completedHandlers = AtomicInteger(0)
    private val completionCallbacks = ConcurrentLinkedQueue<() -> Unit>()
    
    /**
     * 注册处理器数量
     */
    fun registerExpectedHandlers(count: Int) {
        expectedHandlers.set(count)
        SparkCore.LOGGER.info("注册预期的处理器数量: $count")
    }
    
    /**
     * 标记一个处理器的资源提取完成
     */
    fun markHandlerExtractionComplete(handlerName: String) {
        val completed = completedHandlers.incrementAndGet()
        SparkCore.LOGGER.info("处理器 $handlerName 资源提取完成 ($completed/${expectedHandlers.get()})")
        
        if (completed >= expectedHandlers.get() && expectedHandlers.get() > 0) {
            markExtractionComplete()
        }
    }
    
    /**
     * 直接标记所有提取完成
     */
    fun markExtractionComplete() {
        if (isComplete.compareAndSet(false, true)) {
            SparkCore.LOGGER.info("所有资源提取完成，触发完成回调")
            
            // 执行所有等待的回调
            while (!completionCallbacks.isEmpty()) {
                val callback = completionCallbacks.poll()
                try {
                    callback?.invoke()
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("执行资源提取完成回调时出错", e)
                }
            }
        }
    }
    
    /**
     * 检查是否已完成
     */
    fun isExtractionComplete(): Boolean = isComplete.get()
    
    /**
     * 注册完成回调
     */
    fun onExtractionComplete(callback: () -> Unit) {
        if (isComplete.get()) {
            // 如果已经完成，立即执行回调
            try {
                callback()
            } catch (e: Exception) {
                SparkCore.LOGGER.error("执行资源提取完成回调时出错", e)
            }
        } else {
            // 否则加入等待队列
            completionCallbacks.offer(callback)
        }
    }
    
    /**
     * 重置状态（用于测试或重新初始化）
     */
    fun reset() {
        isComplete.set(false)
        expectedHandlers.set(0)
        completedHandlers.set(0)
        completionCallbacks.clear()
        SparkCore.LOGGER.info("资源提取完成追踪器已重置")
    }
}