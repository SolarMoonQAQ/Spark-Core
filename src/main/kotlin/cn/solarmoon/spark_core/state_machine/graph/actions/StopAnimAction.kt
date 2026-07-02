package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.MapCodec

/**
 * 退出状态时停止当前控制器追踪的动画。
 *
 * 实际停止由 [AnimStateMachine.onExit] 统一处理：先移除 Tick handler，再调用父类 onExit。
 * 此 Action 作为显式标记存在，也可用于在 onExit 中提前停止特定动画（当前实现停止全部）。
 */
class StopAnimAction : StateAction {

    override val codec = CODEC

    override fun execute(controller: StateGraphController) {
        val ctrl = controller as? AnimStateMachine ?: run {
            SparkCore.LOGGER.warn("StopAnimAction 只能在 AnimStateMachine 上下文中执行，当前控制器为 {}", controller::class.simpleName)
            return
        }
        ctrl.activeAnimInstances.forEach { it.exit() }
    }

    companion object {
        val CODEC: MapCodec<StopAnimAction> = MapCodec.unit(StopAnimAction())
    }

}
