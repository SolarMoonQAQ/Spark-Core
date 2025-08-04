package cn.solarmoon.spark_core.state_machine

import ru.nsk.kstatemachine.statemachine.StateMachine

interface StateMachineHandler {

    val machine: StateMachine?

    var isActive: Boolean

    fun progress()

}