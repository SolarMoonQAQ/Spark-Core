package cn.solarmoon.spark_core.animation.anim

/**
 * 动画混合层常量 —— 按 bit 位区分，值越大优先级越高。
 *
 * 层优先级（由 [AnimController.blendBone] 按 key 升序遍历）：
 * AMBIENT(1) → LOCOMOTION(2) → POSTURE(4) → ACTION(8) → OVERRIDE(16)
 *
 * 每层内可含多个 [AnimInstance]，由其自身的 [BlendMode] 决定覆盖或叠加。
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

    // === 旧常量别名（兼容期，不建议新代码使用） ===
    @Deprecated("使用 LOCOMOTION", ReplaceWith("LOCOMOTION"))
    const val STATE = LOCOMOTION

    @Deprecated("使用 ACTION", ReplaceWith("ACTION"))
    const val DECOR = ACTION

    @Deprecated("使用 POSTURE", ReplaceWith("POSTURE"))
    const val MAIN = POSTURE
}
