package cn.solarmoon.spark_core.entity

import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object EntityPatchApplier {

    @SubscribeEvent
    private fun onEntityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        val level = entity.level()
        if (entity !is Player && !level.isClientSide) {
            val last = entity.isMoving
            val current = entity.moveCheck()
            if (last != current) {
                entity.isMoving = current
                PacketDistributor.sendToAllPlayers(EntityMovingPayload(entity.id, entity.isMoving))
            }
        }
    }

    @SubscribeEvent
    private fun onPlayerUpdateMove(event: MovementInputUpdateEvent) {
        val player = event.entity
        val input = event.input
        val last = player.isMoving
        val current = input.moveVector.length() > 0
        if (last != current) {
            player.isMoving = current
            PacketDistributor.sendToServer(EntityMovingPayload(player.id, player.isMoving))
        }
    }

}