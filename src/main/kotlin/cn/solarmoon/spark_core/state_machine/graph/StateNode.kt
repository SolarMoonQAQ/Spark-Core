package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

data class StateNode(
    val name: String,
    val transitions: List<StateTransition>,
    val onEntry: List<StateAction> = listOf(),
    val onExit: List<StateAction> = listOf(),
    val subGraph: StateMachineGraph? = null
) {

    val transitionMap = transitions.groupBy { it.event }

    companion object {
        val CODEC: Codec<StateNode> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter(StateNode::name),
                StateTransition.CODEC.listOf().optionalFieldOf("transitions", listOf()).forGetter(StateNode::transitions),
                StateAction.CODEC.listOf().optionalFieldOf("on_entry", listOf()).forGetter(StateNode::onEntry),
                StateAction.CODEC.listOf().optionalFieldOf("on_exit", listOf()).forGetter(StateNode::onExit),
                Codec.lazyInitialized { StateMachineGraph.CODEC }.optionalFieldOf("sub_graph").forGetter { Optional.ofNullable(it.subGraph) }
            ).apply(it) { name, transitions, onEntry, onExit, subGraph ->
                StateNode(name, transitions, onEntry, onExit, subGraph.orElse(null))
            }
        }
    }

}