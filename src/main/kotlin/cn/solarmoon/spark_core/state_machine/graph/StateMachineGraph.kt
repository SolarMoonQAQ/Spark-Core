package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class StateMachineGraph(
    val initialNode: StateNode,
    val nodes: Map<String, StateNode>,
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                StateNode.CODEC.fieldOf("initial_node").forGetter(StateMachineGraph::initialNode),
                Codec.unboundedMap(Codec.STRING, StateNode.CODEC).optionalFieldOf("nodes", mapOf()).forGetter(StateMachineGraph::nodes)
            ).apply(it, ::StateMachineGraph)
        }
    }

}
