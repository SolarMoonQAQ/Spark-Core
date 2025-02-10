package cn.solarmoon.spark_core.entity.player

import cn.solarmoon.spark_core.entity.copy
import net.minecraft.client.player.Input
import net.minecraft.client.player.LocalPlayer
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent

object PlayerApplier {

    object Client {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        private fun savePlayerInput(event: MovementInputUpdateEvent) {
            val entity = event.entity
            val input = event.input
            if (entity.isLocalPlayer) {
                (entity as LocalPlayer).savedInput = input.copy()
            }
        }
    }

}