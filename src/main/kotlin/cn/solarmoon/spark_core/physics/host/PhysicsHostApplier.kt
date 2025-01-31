package cn.solarmoon.spark_core.physics.host

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent

object PhysicsHostApplier {

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.removeAllBodies()
    }

}