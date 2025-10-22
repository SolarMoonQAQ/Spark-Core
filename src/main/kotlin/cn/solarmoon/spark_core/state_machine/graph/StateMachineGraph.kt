package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class StateMachineGraph(
    val initialNode: StateNode,
    val nodes: List<StateNode>,
) {

    val nodeMap = (nodes + initialNode).associateBy { it.name }

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                StateNode.CODEC.fieldOf("initial_node").forGetter(StateMachineGraph::initialNode),
                StateNode.CODEC.listOf().fieldOf("nodes").forGetter(StateMachineGraph::nodes)
            ).apply(it, ::StateMachineGraph)
        }
    }

}
