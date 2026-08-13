package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.MapCodec

/**
 * 驻留时长条件 —— 当前节点进入以来累计时间（[StateGraphController.stateTime]）达到 [duration] 秒。
 *
 * 用于 dodge / stun / hard_land 等"有时长状态"的自动退出：
 * ```
 * dodge ──(stateTime >= 0.25s)──▶ idle / drift
 * ```
 * 时长固定写死在图中；若时长来自变量（如受击事件写入的硬直时长），使用 [StateTimeVariableCondition]。
 */
class StateTimeCondition(
    val duration: Float
) : StateCondition {

    override val codec: MapCodec<out StateCondition> = MapCodec.unit(this)

    override fun check(controller: StateGraphController): Boolean =
        controller.stateTime >= duration

    override fun toString(): String = "StateTime(${duration}s)"
}
