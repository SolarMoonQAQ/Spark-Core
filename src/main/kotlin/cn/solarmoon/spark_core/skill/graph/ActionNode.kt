package cn.solarmoon.spark_core.skill.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

data class ActionNode(
    val id: String,
    val transitions: List<ActionTransition>,
    val subGraph: ActionGraph? = null
) {

    val transitionMap = transitions.groupBy { it.event }

    companion object {
        val CODEC: Codec<ActionNode> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("id").forGetter(ActionNode::id),
                ActionTransition.CODEC.listOf().optionalFieldOf("transitions", listOf()).forGetter(ActionNode::transitions),
                Codec.lazyInitialized { ActionGraph.CODEC }.optionalFieldOf("sub_graph", null as ActionGraph?).forGetter(ActionNode::subGraph)
            ).apply(it, ::ActionNode)
        }
    }

}