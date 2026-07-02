package cn.solarmoon.spark_core.state_machine.graph.conditions

import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.animation.state.MultiAnimStateMachine
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
 * 自动根据控制器类型分路求值：
 * - [AnimStateMachine]：通过 `evalAsBoolean(animatable)` 求值，可访问 `q.anim_time` 等动画相关查询
 * - [MultiAnimStateMachine]：通过 [MolangContextRegistry] 原始求解路径求值，
 *   上下文由 [MultiAnimStateMachine.contextProvider] 提供，不依赖 `q.anim_time`
 */
class MoLangCondition(
    val expression: JSMolangValue
): StateCondition {

    override val codec = CODEC

    override fun check(controller: StateGraphController): Boolean {
        return when (controller) {
            is AnimStateMachine -> {
                // 保持现有路径：通过 IAnimatable 的 MolangContext 求值
                expression.evalAsBoolean(controller.animatable)
            }
            is MultiAnimStateMachine -> {
                // 广播状态机：通过 MolangContext 直接求值，走常量缓存路径
                expression.evalAsBoolean(controller.contextProvider())
            }
            else -> false
        }
    }

    companion object {
        val CODEC: MapCodec<MoLangCondition> = RecordCodecBuilder.mapCodec {
            it.group(
                JSMolangValue.CODEC.fieldOf("expression").forGetter(MoLangCondition::expression)
            ).apply(it, ::MoLangCondition)
        }
    }

}
