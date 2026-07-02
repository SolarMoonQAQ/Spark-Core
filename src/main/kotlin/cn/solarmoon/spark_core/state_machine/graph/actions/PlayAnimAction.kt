package cn.solarmoon.spark_core.state_machine.graph.actions

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.animation.state.MultiAnimStateMachine
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
 * 自动根据控制器类型分路：
 * - [AnimStateMachine]（1:1）：通过 [animInstance] 找 target 自身的动画
 * - [MultiAnimStateMachine]（1:N）：遍历所有 [animTargets]，
 *   每个 target 通过三级回退（自身 → 素体 → 内置）查找动画并广播播放
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
        when (controller) {
            is MultiAnimStateMachine -> {
                for (target in controller.animTargets) {
                    val anim = controller.findAnimation(target, animName) ?: continue
                    // 直接传入已解析的 anim，避免在 target 的 OAnimationSet 中重复查找
                    val instance = AnimInstance(target, anim)
                    instance.inTransitionTime = blendTime
                    instance.outTransitionTime = blendTime
                    instance.group = controller.animGroup
                    applyWeightExpression(instance)
                    instance.enter()  // enter() 内部自动调用 target.animController.playAnimation(this)
                    controller.activeAnimInstances.add(instance)
                }
            }
            is AnimStateMachine -> {
                // 原有 1:1 逻辑不变（保持现有 AnimGroups.LOCOMOTION 默认值）
                val target = controller.animatable
                val instance = animInstance(target, animName) ?: return
                instance.inTransitionTime = blendTime
                instance.outTransitionTime = blendTime
                instance.group = AnimGroups.LOCOMOTION
                applyWeightExpression(instance)
                instance.enter()  // enter() 内部自动调用 target.animController.playAnimation(this)
                controller.activeAnimInstances.add(instance)
            }
            else -> {
                SparkCore.LOGGER.warn(
                    "PlayAnimAction 只能在 AnimStateMachine / MultiAnimStateMachine 上下文中执行，当前控制器为 {}",
                    controller::class.simpleName
                )
            }
        }
    }

    /** 动态权重：利用 AnimInstance 现有的 onEvent 机制，每 tick 重新求值 */
    private fun applyWeightExpression(instance: AnimInstance) {
        if (weightExpression != null) {
            val exp = weightExpression
            instance.onEvent<AnimEvent.Tick> {
                weight = exp.evalAsDouble(this).toFloat()
            }
        }
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
