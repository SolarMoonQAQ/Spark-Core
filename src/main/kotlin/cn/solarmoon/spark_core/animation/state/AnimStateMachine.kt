package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking

class AnimStateMachine(
    val animatable: IAnimatable<*>,
    val animations: MutableMap<String, Map<String, AnimInstance>>,
    val stateMachine: StateMachine
) {

    object TickEvent: Event

    fun tick() {
        stateMachine.processEventBlocking(TickEvent)
    }

}