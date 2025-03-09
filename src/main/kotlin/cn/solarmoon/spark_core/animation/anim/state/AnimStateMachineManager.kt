package cn.solarmoon.spark_core.animation.anim.state

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.nsk.kstatemachine.statemachine.StateMachine

object AnimStateMachineManager {

    private val serverStateMachines = hashMapOf<Int, StateMachine>()
    private val clientStateMachines = hashMapOf<Int, StateMachine>()

    fun putStateMachine(entity: Entity, level: Level, machine: StateMachine) {
        if (level.isClientSide) {
            clientStateMachines[entity.id] = machine
        } else {
            serverStateMachines[entity.id] = machine
        }
    }

    fun getStateMachine(entity: Entity) = if (entity.level().isClientSide) clientStateMachines[entity.id] else serverStateMachines[entity.id]

}