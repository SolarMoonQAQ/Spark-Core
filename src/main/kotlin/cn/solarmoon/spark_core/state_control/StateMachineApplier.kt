package cn.solarmoon.spark_core.state_control

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object StateMachineApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.getAllStateMachines().values.forEach { it.handleState() }
    }

}