package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class ActionGraph(
    val initialNode: ActionNode,
    val nodes: Map<String, ActionNode>,
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                ActionNode.CODEC.fieldOf("initial_node").forGetter(ActionGraph::initialNode),
                Codec.unboundedMap(Codec.STRING, ActionNode.CODEC).optionalFieldOf("nodes", mapOf()).forGetter(ActionGraph::nodes)
            ).apply(it, ::ActionGraph)
        }
    }

}
