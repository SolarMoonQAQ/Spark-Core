package cn.solarmoon.spark_core.skill.input_buffer

data class InputEvent(
    val type: String,              // 输入类型
    val durationTicks: Int,        // 有效期（tick）
    val tickInserted: Int,          // 插入时的游戏 tick
    val priority: Int = 0
) {
    val expireTick = tickInserted + durationTicks

    fun isValid(currentTick: Int): Boolean {
        return currentTick < expireTick
    }
}
