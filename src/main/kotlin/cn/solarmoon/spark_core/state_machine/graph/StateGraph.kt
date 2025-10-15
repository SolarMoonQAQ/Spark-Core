package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class StateGraph(
    val initialNode: StateNode,
    val nodes: Map<String, StateNode>,
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                StateNode.CODEC.fieldOf("initial_node").forGetter(StateGraph::initialNode),
                Codec.unboundedMap(Codec.STRING, StateNode.CODEC).optionalFieldOf("nodes", mapOf()).forGetter(StateGraph::nodes)
            ).apply(it, ::StateGraph)
        }
    }

}
