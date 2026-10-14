package cn.solarmoon.spark_core.animation.anim

/**
 * 动画混合层常量 —— 语义层为 2 的幂，常驻段为低位连续整数；值越大优先级越高。
 *
 * 层优先级（由 [AnimController.blendBone] 按 key 升序遍历）：
 * 常驻段(-8..-1) → AMBIENT(1) → LOCOMOTION(2) → POSTURE(4) → ACTION(8) → OVERRIDE(16)
 *
 * 每层内可含多个 [AnimInstance]，由其自身的 [BlendMode] 决定覆盖或叠加。
 * 层号 0 与正数区间（除已定义的语义层外）保持空闲，留给以后可能新增的语义层。
 */
object AnimGroups {

    /** 环境/背景（头发物理、披风、纯视觉效果） */
    const val AMBIENT = 1 shl 0

    /** 基础运动（行走、跑步、游泳、待机） */
    const val LOCOMOTION = 1 shl 1

    /** 姿态覆盖（持物、瞄准、骑乘） */
    const val POSTURE = 1 shl 2

    /** 动作覆盖（挥动、使用、开火） */
    const val ACTION = 1 shl 3

    /** 强制覆盖（死亡、布娃娃、过场） */
    const val OVERRIDE = 1 shl 4

    // === 常驻低位段（整体低于全部语义层） ===

    /** 常驻段最低槽对应的层号 */
    const val PERSISTENT_BASE = -8

    /** 常驻段槽位数：槽位号 0..7 → 层号 -8..-1 */
    const val PERSISTENT_SLOTS = 8

    /**
     * 取常驻段第 [slot] 个槽位（0 起）对应的层号。
     *
     * 常驻层整体低于 [AMBIENT]，用于「若干常驻动画各占一层、不被任何语义层覆盖」的场合。
     *
     * @throws IllegalArgumentException [slot] 不在 [PERSISTENT_BASE] 对应的合法范围内
     */
    fun persistent(slot: Int): Int {
        require(slot in 0 until PERSISTENT_SLOTS) { "常驻层槽位越界：$slot（合法范围 0 until " + PERSISTENT_SLOTS + "）" }
        return PERSISTENT_BASE + slot
    }

    // === 旧常量别名（兼容期，不建议新代码使用） ===
    @Deprecated("使用 LOCOMOTION", ReplaceWith("LOCOMOTION"))
    const val STATE = LOCOMOTION

    @Deprecated("使用 ACTION", ReplaceWith("ACTION"))
    const val DECOR = ACTION

    @Deprecated("使用 POSTURE", ReplaceWith("POSTURE"))
    const val MAIN = POSTURE
}
