package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.evalAsDouble
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 执行一段 MoLang 脚本（通常用于 on_entry / on_exit 中修改变量）。
 */
class MoLangAction(
    val script: JSMolangValue
) : StateAction {

    override val codec = CODEC

    override fun execute(controller: StateGraphController) {
        val ctrl = controller as? AnimStateMachine ?: run {
            SparkCore.LOGGER.warn("MoLangAction 只能在 AnimStateMachine 上下文中执行，当前控制器为 {}", controller::class.simpleName)
            return
        }
        script.evalAsDouble(ctrl.animatable)
    }

    companion object {
        val CODEC: MapCodec<MoLangAction> = RecordCodecBuilder.mapCodec {
            it.group(
                JSMolangValue.CODEC.fieldOf("script").forGetter(MoLangAction::script)
            ).apply(it, ::MoLangAction)
        }
    }

}
