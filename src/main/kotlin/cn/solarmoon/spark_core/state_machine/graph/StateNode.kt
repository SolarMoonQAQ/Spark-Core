package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class StateNode(
    val id: String,
    val transitions: List<StateTransition>,
    val onEnter: List<StateAction> = listOf(),
    val onExit: List<StateAction> = listOf(),
    val subGraph: StateMachineGraph? = null
) {

    val transitionMap = transitions.groupBy { it.event }

    companion object {
        val CODEC: Codec<StateNode> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("id").forGetter(StateNode::id),
                StateTransition.CODEC.listOf().optionalFieldOf("transitions", listOf()).forGetter(StateNode::transitions),
                StateAction.CODEC.listOf().optionalFieldOf("on_enter", listOf()).forGetter(StateNode::onEnter),
                StateAction.CODEC.listOf().optionalFieldOf("on_exit", listOf()).forGetter(StateNode::onExit),
                Codec.lazyInitialized { StateMachineGraph.CODEC }.optionalFieldOf("sub_graph", null as StateMachineGraph?).forGetter(StateNode::subGraph)
            ).apply(it, ::StateNode)
        }
    }

}