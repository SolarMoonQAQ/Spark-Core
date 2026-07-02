package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

data class StateNode(
    val name: String,
    val transitions: List<StateTransition>,
    val onEntry: List<StateAction> = listOf(),
    val onExit: List<StateAction> = listOf(),
    val subGraphs: Map<String, StateMachineGraph> = mapOf()
) {

    /** event != null 的转移，按 event 分组（事件驱动路径，O(1) 查找） */
    val eventTransitions = transitions.filter { it.event != null }.groupBy { it.event!! }

    /** event == null 的转移，每 tick 自动求值（Bedrock 模式路径） */
    val autoTransitions = transitions.filter { it.event == null }

    companion object {
        val CODEC: Codec<StateNode> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("name").forGetter(StateNode::name),
                StateTransition.CODEC.listOf().optionalFieldOf("transitions", listOf()).forGetter(StateNode::transitions),
                StateAction.CODEC.listOf().optionalFieldOf("on_entry", listOf()).forGetter(StateNode::onEntry),
                StateAction.CODEC.listOf().optionalFieldOf("on_exit", listOf()).forGetter(StateNode::onExit),
                Codec.unboundedMap(Codec.STRING, Codec.lazyInitialized { StateMachineGraph.CODEC }).optionalFieldOf("sub_graphs", mapOf()).forGetter(StateNode::subGraphs)
            ).apply(it, ::StateNode)
        }
    }

}