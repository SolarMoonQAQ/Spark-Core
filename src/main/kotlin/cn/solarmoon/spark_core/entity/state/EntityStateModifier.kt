package cn.solarmoon.spark_core.entity.state

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

class EntityStateModifier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Post) {
        val entity = event.entity
        val level = entity.level()

        if (!level.isClientSide) {
            if (entity.moveCheck() && !entity.isMoving()) {
                entity.setMoving(true)
            } else if (!entity.moveCheck() && entity.isMoving()) {
                entity.setMoving(false)
            }

            if (entity.moveBackCheck() && !entity.isMovingBack()) {
                entity.setMovingBack(true)
            } else if (!entity.moveBackCheck() && entity.isMovingBack()) {
                entity.setMovingBack(false)
            }

            if (entity.isJumping()) {
                entity.setJumpingState(false)
            }

            entity.setServerMoveSpeed(entity.getMoveSpeed().toFloat())
        }
    }

    @SubscribeEvent
    private fun jump(event: LivingEvent.LivingJumpEvent) {
        val entity = event.entity
        entity.setJumpingState(true)
    }

}