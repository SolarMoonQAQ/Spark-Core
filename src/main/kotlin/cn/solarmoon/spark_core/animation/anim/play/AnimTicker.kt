package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.entity.state.isJumping
import cn.solarmoon.spark_core.entity.state.isMoving
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AnimTicker {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.animController.tick()
        }
    }

    @SubscribeEvent
    private fun onBoneUpdate(event: BoneUpdateEvent) {
        val animatable = event.animatable
        animatable.onBoneUpdate(event)
    }

}