package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.state_machine.StateMachineHandler
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.Event

abstract class StateMachineRegisterEvent(
    val stateMachineHandlers: MutableMap<ResourceLocation, StateMachineHandler>
): Event() {

    fun register(id: ResourceLocation, handler: StateMachineHandler) {
        stateMachineHandlers[id] = handler
    }

    class Entity(
        stateMachineHandlers: MutableMap<ResourceLocation, StateMachineHandler>,
        val entity: net.minecraft.world.entity.Entity
    ): StateMachineRegisterEvent(stateMachineHandlers) {

    }

}