package cn.solarmoon.spark_core.entity.player

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
                (entity as LocalPlayer).savedInput = Input().apply {
                    leftImpulse = input.leftImpulse
                    forwardImpulse = input.forwardImpulse
                    up = input.up
                    down = input.down
                    left = input.left
                    right = input.right
                    jumping = input.jumping
                    shiftKeyDown = input.shiftKeyDown
                }
            }
        }
    }

}