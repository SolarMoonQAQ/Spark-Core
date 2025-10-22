package cn.solarmoon.spark_core.state_machine.graph

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.transition

data class StateTransition(
    val event: String,
    val target: String,
    val condition: StateCondition
) {

    companion object {
        val CODEC = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("event").forGetter(StateTransition::event),
                Codec.STRING.fieldOf("target").forGetter(StateTransition::target),
                StateCondition.CODEC.optionalFieldOf("condition", StateCondition.True).forGetter(StateTransition::condition)
            ).apply(it, ::StateTransition)
        }
    }

}