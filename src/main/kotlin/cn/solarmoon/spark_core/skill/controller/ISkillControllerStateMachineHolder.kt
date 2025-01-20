package cn.solarmoon.spark_core.skill.controller

import ru.nsk.kstatemachine.statemachine.StateMachine

interface ISkillControllerStateMachineHolder {

    var stateMachine: StateMachine?

}