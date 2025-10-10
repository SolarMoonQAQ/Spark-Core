package cn.solarmoon.spark_core.skill.input

// 输入缓冲器
class InputBuffer {

    // 实时缓冲：只存短期有效输入
    private val realtimeQueue = ArrayDeque<InputEvent>()

    // 输入日志：完整记录所有输入（包括过期的）
    private val historyLog = mutableListOf<InputEvent>()

    // 添加输入
    fun push(input: InputEvent) {
        // 记录到日志
        historyLog.add(input)
        // 放入实时缓冲
        realtimeQueue.addLast(input)
    }

    // 获取下一个有效输入（并移除）
    fun popValid(
        currentTick: Int,
        mode: InputBufferTriggerMode = InputBufferTriggerMode.LAST_INPUT
    ): InputEvent? {
        while (realtimeQueue.isNotEmpty() && !realtimeQueue.first().isValid(currentTick)) {
            realtimeQueue.removeFirst()
        }
        if (realtimeQueue.isEmpty()) return null

        val chosen = when (mode) {
            InputBufferTriggerMode.LAST_INPUT -> realtimeQueue.maxByOrNull { it.tickInserted }
            InputBufferTriggerMode.HIGHEST_PRIORITY -> realtimeQueue.maxByOrNull { it.priority }
        }
        chosen?.let { realtimeQueue.remove(it) }
        return chosen
    }

    // 获取完整日志（包括过期输入）
    fun getHistory(): List<InputEvent> = historyLog.toList()

    // 清空日志（例如一局游戏结束时）
    fun clearHistory() {
        historyLog.clear()
    }

}