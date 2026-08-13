package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import cn.solarmoon.spark_core.state_machine.graph.StateVariableKey
import com.mojang.serialization.MapCodec

/**
 * 冷却条件 —— 距 [timestampKey] 记录的时刻（由 [RecordTimeAction] 写入 [StateGraphController.controllerTime]）
 * 已过去 [minElapsed] 秒。
 *
 * 语义：**从未使用过（key 未显式写入）时直接放行**——第一次闪避/技能无需等待冷却。
 * 与 [RecordTimeAction] 配对使用：
 * ```
 * // dodge 进入时记录冷却起点；其余状态进入 dodge 需满足冷却
 * onEntry  = [RecordTimeAction(LAST_DODGE_TIME)]
 * 转移条件 = ElapsedTimeCondition(LAST_DODGE_TIME, 0.6f)
 * ```
 */
class ElapsedTimeCondition(
    val timestampKey: StateVariableKey<Float>,
    val minElapsed: Float
) : StateCondition {

    override val codec: MapCodec<out StateCondition> = MapCodec.unit(this)

    override fun check(controller: StateGraphController): Boolean {
        val vars = controller.variables
        if (!vars.contains(timestampKey)) return true // 从未使用过 → 无冷却
        return controller.controllerTime - vars.get(timestampKey) >= minElapsed
    }

    override fun toString(): String = "Elapsed(${timestampKey.id} >= ${minElapsed}s)"
}
