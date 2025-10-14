package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.choiceState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine

data class OAnimStateMachine(
    val initialState: String,
    val states: Map<String, OAnimState>
) {

    fun build(animatable: IAnimatable<*>): AnimStateMachine {
        val exp = ExpressionEvaluator.evaluator(animatable)
        // 每个状态对应一个 Map<动画名, AnimInstance>
        val stateAnims = mutableMapOf<String, Map<String, AnimInstance>>()

        val stateMachine = createStdLibStateMachine {
            val stateMap = mutableMapOf<String, State>()

            this@OAnimStateMachine.states.forEach { (stateName, animState) ->
                // 为该状态生成动画实例 Map
                val anims = animState.animations.mapValues { (animName, animWeight) ->
                    animInstance(animatable, animName)!!.apply {
                        group = AnimGroups.STATE
                        inTransitionTime = animState.blendTransition
                        onEvent<AnimEvent.Tick> { weight = animWeight.evalAsDouble(exp).toFloat() }
                    }
                }

                val s = if (stateName == this@OAnimStateMachine.initialState) initialState(stateName) else state(stateName)

                // 绑定状态和动画 Map
                stateAnims[stateName] = anims

                s.apply {
                    onEntry {
                        SparkCore.LOGGER.info("进入状态: $stateName，Animations: ${anims.keys}")
                        anims.values.forEach { it.enter() }
                        animState.onEntry.evalUnsafe(exp)
                    }
                    onExit {
                        anims.values.forEach { it.exit() }
                        animState.onExit.evalUnsafe(exp)
                    }
                }

                stateMap[stateName] = s
            }

            // 添加 transition
            this@OAnimStateMachine.states.forEach { (stateName, animState) ->
                val fromState = stateMap[stateName]!!
                val choice = choiceState {
                    animState.transitions.forEach { (targetName, condition) ->
                        val targetState = stateMap[targetName]!!
                        if (condition.evalAsBoolean(exp)) return@choiceState targetState
                    }
                    fromState
                }
                fromState.transition<AnimStateMachine.TickEvent> {
                    this.targetState = choice
                }
            }
        }

        // 返回时把 stateAnims 一起交给 AnimStateMachine
        return AnimStateMachine(animatable, stateAnims, stateMachine)
    }

    companion object {
        val CODEC: Codec<OAnimStateMachine> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.STRING.fieldOf("initial_state").forGetter { it.initialState },
                Codec.unboundedMap(Codec.STRING, OAnimState.CODEC).fieldOf("states").forGetter { it.states }
            ).apply(ins, ::OAnimStateMachine)
        }
    }

}