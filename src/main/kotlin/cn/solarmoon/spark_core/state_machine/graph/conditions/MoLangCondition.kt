package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.evalAsBoolean
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 使用 MoLang 表达式作为状态转移条件。
 *
 * 构造时接受 [JSMolangValue] 以避免字符串 → AST 的重复解析。
 * 在 [AnimStateMachine] 上下文中求值时，可访问 `q.all_animations_finished` 等动画相关查询。
 */
class MoLangCondition(
    val expression: JSMolangValue
): StateCondition {

    override val codec = CODEC

    override fun check(controller: StateGraphController): Boolean {
        val animController = controller as? AnimStateMachine
            ?: run {
                // 非动画状态机上下文：无法求值动画相关查询，但仍尝试用持有者求值
                return false
            }
        return expression.evalAsBoolean(animController.animatable)
    }

    companion object {
        val CODEC: MapCodec<MoLangCondition> = RecordCodecBuilder.mapCodec {
            it.group(
                JSMolangValue.CODEC.fieldOf("expression").forGetter(MoLangCondition::expression)
            ).apply(it, ::MoLangCondition)
        }
    }

}
