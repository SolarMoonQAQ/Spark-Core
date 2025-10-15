package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class StateTransition(
    val event: String,
    val source: String,
    val target: String,
    val condition: StateCondition
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("event").forGetter(StateTransition::event),
                Codec.STRING.fieldOf("source").forGetter(StateTransition::source),
                Codec.STRING.fieldOf("target").forGetter(StateTransition::target),
                StateCondition.CODEC.optionalFieldOf("condition", StateCondition.True).forGetter(StateTransition::condition)
            ).apply(it, ::StateTransition)
        }
    }

}