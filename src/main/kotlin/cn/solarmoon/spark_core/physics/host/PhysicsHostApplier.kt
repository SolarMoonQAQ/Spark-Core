package cn.solarmoon.spark_core.physics.host

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object PhysicsHostApplier {

    @SubscribeEvent
    private fun syncState(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.updatePhysicsState()
    }

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.removeAllBodies()
    }

}