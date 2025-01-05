package cn.solarmoon.spark_core.entity.state

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.part.BonePart
import cn.solarmoon.spark_core.animation.model.part.Locator
import com.mojang.serialization.JsonOps
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object EntityStateModifier {

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