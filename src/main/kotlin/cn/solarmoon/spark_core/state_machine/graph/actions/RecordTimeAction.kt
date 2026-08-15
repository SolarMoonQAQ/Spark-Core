package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateVariableKey
import com.mojang.serialization.MapCodec

/**
 * 进入节点时把当前 [StateGraphController.controllerTime] 写入变量 —— 冷却起点。
 *
 * 与 [cn.solarmoon.spark_core.state_machine.graph.conditions.ElapsedTimeCondition] 配对，
 * 用于 dodge / 技能等"有最短间隔"的转移门控。作为 onEntry 动作执行，
 * 记录的是进入该节点的时刻；节点重复进入会刷新记录。
 */
class RecordTimeAction(
    val key: StateVariableKey<Float>
) : StateAction {

    override fun execute(controller: StateGraphController) {
        controller.variables.set(key, controller.controllerTime)
    }

    override val codec: MapCodec<out StateAction> = MapCodec.unit(this)

    override fun toString(): String = "RecordTime(${key.id})"
}
