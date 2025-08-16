package cn.solarmoon.spark_core.preinput

import cn.solarmoon.spark_core.event.OnPreInputExecuteEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.ArrayDeque

/**
 * 优化后的预输入系统 - 符合常规动作游戏设计
 * 特点：
 * 1. 先进先出（FIFO）策略
 * 2. 限制最大缓存数量（默认保留最近4个输入）
 * 3. 自动清理过期输入（默认0.5秒）
 */
class PreInput(
    val holder: IPreInputHolder,
) {

    private val maxBufferSize: Int = 4 // 默认保留最近4个输入
    private val inputBuffer = ArrayDeque<PreInputData>(maxBufferSize)
    var isLocked = false
        private set

    val hasInput get() = inputBuffer.isNotEmpty()

    fun hasInput(id: String): Boolean {
        return inputBuffer.any { it.id == id }
    }

    /**
     * 添加预输入
     * @param id 输入标识
     * @param maxRemainTime 最大存续时间（单位：tick）
     * @param input 要执行的输入动作
     */
    fun setInput(id: String, maxRemainTime: Int = 5, input: () -> Unit) {
        require(maxRemainTime > 0) { "预输入存续时间必须大于0" }

        // 先查找是否已有相同 ID 的输入
        val existing = inputBuffer.firstOrNull { it.id == id }
        if (existing != null) {
            // 覆盖旧输入内容 & 重置计时
            existing.input = input
            existing.remain = 0
            existing.maxRemainTime = maxRemainTime
            return
        }

        // 没有则按 FIFO 策略入队
        if (inputBuffer.size >= maxBufferSize) {
            inputBuffer.removeFirst()
        }
        inputBuffer.add(PreInputData(id, input, 0, maxRemainTime))
    }

    private fun invoke(data: PreInputData) {
        val event = NeoForge.EVENT_BUS.post(OnPreInputExecuteEvent.Pre(this, data))
        if (event.isCanceled) return
        data.input.invoke()
        NeoForge.EVENT_BUS.post(OnPreInputExecuteEvent.Post(this, data))
    }

    fun execute(extra: () -> Unit = {}): Boolean {
        inputBuffer.pollFirst()?.let {
            extra()
            invoke(it)
            return true
        }
        return false
    }

    fun executeIfPresent(vararg id: String, extra: () -> Unit = {}): Boolean {
        inputBuffer.firstOrNull { it.id in id }?.let {
            extra()
            invoke(it)
            inputBuffer.remove(it)
            return true
        }
        return false
    }

    fun executeExcept(vararg id: String, extra: () -> Unit = {}): Boolean {
        inputBuffer.firstOrNull { it.id !in id }?.let {
            extra()
            invoke(it)
            inputBuffer.remove(it)
            return true
        }
        return false
    }

    /**
     * 每tick更新，清理过期输入
     */
    fun tick() {
        // 更新所有输入的存续时间
        inputBuffer.forEach { it.remain++ }

        // 移除所有过期的输入
        inputBuffer.removeIf { it.remain >= it.maxRemainTime }

        if (!isLocked) {
            execute()
        }
    }

    fun clear() {
        inputBuffer.clear()
    }

    fun lock() {
        isLocked = true
    }

    fun unlock() {
        isLocked = false
    }

    /**
     * 获取当前缓冲区中的所有输入（按添加顺序）
     */
    fun getInputSequence(): List<String> {
        return inputBuffer.map { it.id }
    }

}