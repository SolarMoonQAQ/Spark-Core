package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class StateNode(
    val id: String,
    val transitions: List<StateTransition>,
    val subGraph: StateGraph? = null
) {

    val transitionMap = transitions.groupBy { it.event }

    companion object {
        val CODEC: Codec<StateNode> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("id").forGetter(StateNode::id),
                StateTransition.CODEC.listOf().optionalFieldOf("transitions", listOf()).forGetter(StateNode::transitions),
                Codec.lazyInitialized { StateGraph.CODEC }.optionalFieldOf("sub_graph", null as StateGraph?).forGetter(StateNode::subGraph)
            ).apply(it, ::StateNode)
        }
    }

}