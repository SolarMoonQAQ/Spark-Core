package cn.solarmoon.spark_core.entity

import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object EntityPatchApplier {

    @SubscribeEvent
    private fun onEntityTick(event: EntityTickEvent.Pre) {
    }

    @SubscribeEvent
    private fun onPlayerUpdateMove(event: MovementInputUpdateEvent) {
    }

}