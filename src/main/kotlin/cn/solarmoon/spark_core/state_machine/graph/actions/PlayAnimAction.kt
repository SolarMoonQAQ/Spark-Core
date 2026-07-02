package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.evalAsDouble
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateGraphController
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 进入状态时播放动画。
 *
 * @param animName 动画名
 * @param blendTime 进入/退出混合时间（秒）
 * @param blendViaShortestPath 是否通过最短路径混合旋转（当前仅作为参数保留，具体实现由动画系统处理）
 * @param weightExpression 动态权重 MoLang 表达式；null 表示固定权重 1.0
 */
class PlayAnimAction(
    val animName: String,
    val blendTime: Float = 0.15f,
    val blendViaShortestPath: Boolean = false,
    val weightExpression: JSMolangValue? = null
) : StateAction {

    override val codec = CODEC

    override fun execute(controller: StateGraphController) {
        val ctrl = controller as? AnimStateMachine ?: run {
            SparkCore.LOGGER.warn("PlayAnimAction 只能在 AnimStateMachine 上下文中执行，当前控制器为 {}", controller::class.simpleName)
            return
        }
        val instance = animInstance(ctrl.animatable, animName) ?: run {
            SparkCore.LOGGER.warn("状态 [{}] 引用不存在的动画 [{}]，已跳过", ctrl.currentNode.name, animName)
            return
        }
        instance.inTransitionTime = blendTime
        instance.outTransitionTime = blendTime
        instance.group = AnimGroups.STATE

        // 动态权重：利用 AnimInstance 现有的 onEvent 机制，每 tick 重新求值
        if (weightExpression != null) {
            instance.onEvent<AnimEvent.Tick> {
                weight = weightExpression.evalAsDouble(this).toFloat()
            }
        }

        instance.enter()
        ctrl.activeAnimInstances.add(instance)
    }

    companion object {
        val CODEC: MapCodec<PlayAnimAction> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("anim").forGetter(PlayAnimAction::animName),
                Codec.FLOAT.optionalFieldOf("blend_time", 0.15f).forGetter(PlayAnimAction::blendTime),
                Codec.BOOL.optionalFieldOf("blend_via_shortest_path", false).forGetter(PlayAnimAction::blendViaShortestPath),
                JSMolangValue.CODEC.optionalFieldOf("weight").forGetter { java.util.Optional.ofNullable(it.weightExpression) }
            ).apply(it) { anim, blend, shortest, weight ->
                PlayAnimAction(anim, blend, shortest, weight.orElse(null))
            }
        }
    }

}
