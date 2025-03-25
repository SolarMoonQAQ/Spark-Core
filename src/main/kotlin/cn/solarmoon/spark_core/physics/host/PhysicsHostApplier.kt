package cn.solarmoon.spark_core.physics.host

import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

object PhysicsHostApplier {

    @SubscribeEvent
    private fun syncState(event: LevelTickEvent.Pre) {
        event.level.physicsLevel.world.pcoList.forEach {
            it.sync.swapBuffers()
        }
    }

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.removeAllBodies()
    }

}