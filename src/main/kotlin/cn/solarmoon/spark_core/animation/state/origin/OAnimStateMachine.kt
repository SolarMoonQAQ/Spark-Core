package cn.solarmoon.spark_core.animation.state.origin

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.state.AnimStateMachine
import cn.solarmoon.spark_core.gas.GameplayTagContainer
import cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph
import cn.solarmoon.spark_core.state_machine.graph.StateNode
import cn.solarmoon.spark_core.state_machine.graph.StateVariableContainer
import cn.solarmoon.spark_core.state_machine.graph.actions.MoLangAction
import cn.solarmoon.spark_core.state_machine.graph.actions.ParticleAction
import cn.solarmoon.spark_core.state_machine.graph.actions.PlayAnimAction
import cn.solarmoon.spark_core.state_machine.graph.actions.SoundAction
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class OAnimStateMachine(
    val initialState: String,
    val states: Map<String, OAnimState>
) {

    /**
     * 单独构建 [AnimStateMachine]（无子控制器上下文）。
     *
     * @param animatable 动画目标
     * @param variables  共享变量容器，可传入外部容器；null 则自建
     * @param tags       共享标签容器，可传入外部容器；null 则自建
     */
    fun build(
        animatable: IAnimatable<*>,
        variables: StateVariableContainer? = null,
        tags: GameplayTagContainer? = null
    ): AnimStateMachine {
        // 单独构建时无子控制器上下文，传空 resolver
        return AnimStateMachine(toStateMachineGraph { null }, animatable, variables = variables, tags = tags).also { it.start() }
    }

    /**
     * 将 Bedrock JSON 编译为纯数据 [StateMachineGraph]。
     *
     * @param resolver 控制器名 → [StateMachineGraph] 查找闭包，返回 null 表示不是控制器引用
     */
    fun toStateMachineGraph(
        resolver: (String) -> StateMachineGraph?
    ): StateMachineGraph {
        val nodes = states.map { (stateName, os) ->
            val subGraphs = mutableMapOf<String, StateMachineGraph>()
            val animActions = mutableListOf<PlayAnimAction>()

            os.animations.forEach { (name, weightExpr) ->
                // 1. 优先按控制器名解析 → subGraph
                val subGraph = resolver(name)
                if (subGraph != null) {
                    subGraphs[name] = subGraph  // key = 控制器名
                    return@forEach
                }
                // 2. 其余按动画名处理（存在性校验由 PlayAnimAction.execute() 在运行时完成）
                animActions.add(PlayAnimAction(name, os.blendTransition, os.blendViaShortestPath, weightExpr))
            }

            StateNode(
                name = stateName,
                transitions = os.transitions.map { (target, trigger) -> OTransitionTrigger.from(trigger).toTransition(target) },
                onEntry = buildList {
                    addAll(animActions)
                    os.onEntry.takeIf { it.value != "0" }?.let { add(MoLangAction(it)) }
                    os.soundEffects.forEach { add(SoundAction(it)) }
                    os.particleEffects.forEach { add(ParticleAction(
                        effect = it.effect ?: "",
                        locator = it.locator,
                        bindToActor = it.bindToActor,
                        preEffectScript = it.preEffectScript.takeIf { s -> s.value != "0" }
                    )) }
                },
                onExit = os.onExit.takeIf { it.value != "0" }?.let { listOf(MoLangAction(it)) } ?: listOf(),
                subGraphs = subGraphs
            )
        }
        return StateMachineGraph(
            initialNode = nodes.first { it.name == initialState },
            nodes = nodes
        )
    }

    companion object {
        val CODEC: Codec<OAnimStateMachine> = RecordCodecBuilder.create { ins ->
            ins.group(
                Codec.STRING.optionalFieldOf("initial_state")
                    .forGetter { java.util.Optional.of(it.initialState) },
                Codec.unboundedMap(Codec.STRING, OAnimState.CODEC)
                    .xmap({ LinkedHashMap(it) }, { LinkedHashMap(it) })
                    .fieldOf("states")
                    .forGetter { LinkedHashMap(it.states) }
            ).apply(ins) { initialState, states ->
                OAnimStateMachine(
                    initialState.orElseGet {
                        states.keys.firstOrNull() ?: error("动画控制器 states 不能为空")
                    },
                    states
                )
            }
        }
    }

}
