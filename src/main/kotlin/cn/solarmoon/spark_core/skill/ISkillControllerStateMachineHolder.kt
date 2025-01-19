package cn.solarmoon.spark_core.skill

import ru.nsk.kstatemachine.statemachine.StateMachine

interface ISkillControllerStateMachineHolder {

    var stateMachine: StateMachine?

}