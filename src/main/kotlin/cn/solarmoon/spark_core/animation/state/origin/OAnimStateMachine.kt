package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimGroups
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.js.molang.evalAsBoolean
import cn.solarmoon.spark_core.js.molang.evalAsDouble
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.targetState

data class OAnimStateMachine(
    val initialState: String,
    val states: Map<String, OAnimState>
) {

    fun build(animatable: IAnimatable<*>): AnimStateMachine {
        // 每个状态对应一个 Map<动画名, AnimInstance>
        val stateAnims = mutableMapOf<String, Map<String, AnimInstance>>()

        val stateMachine = createStdLibStateMachine {
            val stateMap = mutableMapOf<String, State>()

            this@OAnimStateMachine.states.forEach { (stateName, animState) ->
                // 为该状态生成动画实例 Map
                val anims = buildMap {
                    animState.animations.forEach { (animName, animWeight) ->
                        val anim = animInstance(animatable, animName)
                        if (anim == null) {
                            SparkCore.LOGGER.warn("状态机状态 [{}] 引用不存在动画 [{}]，已跳过", stateName, animName)
                            return@forEach
                        }
                        put(animName, anim.apply {
                            group = AnimGroups.STATE
                            inTransitionTime = animState.blendTransition
                            outTransitionTime = animState.blendTransition
                            onEvent<AnimEvent.Tick> { weight = animWeight.eval(this).asFloat() }
                        })
                    }
                }

                val s = if (stateName == this@OAnimStateMachine.initialState) initialState(stateName) else state(stateName)

                // 绑定状态和动画 Map
                stateAnims[stateName] = anims

                s.apply {
                    onEntry {
//                        SparkCore.LOGGER.info("进入状态: $stateName，Animations: ${anims.keys}")
                        anims.values.forEach { it.enter() }
                        animState.onEntry.evalAsDouble(animatable)
                    }
                    onExit {
                        anims.values.forEach { it.exit() }
                        animState.onExit.evalAsDouble(animatable)
                    }
                }

                stateMap[stateName] = s
            }

            // 添加 transition
            this@OAnimStateMachine.states.forEach { (stateName, animState) ->
                val fromState = stateMap[stateName]!!
                fromState.transitionConditionally<AnimStateMachine.TickEvent> {
                    direction = {
                        val nextState = animState.transitions.entries.firstNotNullOfOrNull { (targetName, condition) ->
                            val target = stateMap[targetName]
                            if (target == null) {
                                SparkCore.LOGGER.warn("状态机状态 [{}] 转移目标 [{}] 不存在，已跳过该转移", stateName, targetName)
                                return@firstNotNullOfOrNull null
                            }
                            if (condition.evalAsBoolean(animatable)) target else null
                        }

                        if (nextState == null || nextState == fromState) {
                            noTransition()
                        } else {
                            targetState(nextState)
                        }
                    }
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