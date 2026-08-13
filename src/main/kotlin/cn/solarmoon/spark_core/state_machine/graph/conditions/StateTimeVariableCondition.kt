package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateVariableKey
import com.mojang.serialization.MapCodec

/**
 * 驻留时长条件（变量版）—— 时长阈值从变量读取，而不是写死在图中。
 *
 * 典型场景：stun 的硬直时长由事件源决定。受击方在广播 stun 事件前写入
 * `STUN_DURATION = 0.5`，状态机在 `stateTime >= STUN_DURATION` 时自动退出，
 * 不同攻击来源可携带不同硬直时长。
 */
class StateTimeVariableCondition(
    val durationKey: StateVariableKey<Float>
) : StateCondition {

    override val codec: MapCodec<out StateCondition> = MapCodec.unit(this)

    override fun check(controller: StateGraphController): Boolean =
        controller.stateTime >= controller.variables.get(durationKey)

    override fun toString(): String = "StateTimeVar(${durationKey.id})"
}
