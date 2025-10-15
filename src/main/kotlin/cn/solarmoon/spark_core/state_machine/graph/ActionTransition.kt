package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class ActionTransition(
    val event: String,
    val source: String,
    val target: String,
    val condition: ActionCondition
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("event").forGetter(ActionTransition::event),
                Codec.STRING.fieldOf("source").forGetter(ActionTransition::source),
                Codec.STRING.fieldOf("target").forGetter(ActionTransition::target),
                ActionCondition.CODEC.optionalFieldOf("condition", ActionCondition.True).forGetter(ActionTransition::condition)
            ).apply(it, ::ActionTransition)
        }
    }

}