package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AnimTicker {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.animController.tick()

            if (Minecraft.getInstance().options.keyAttack.isDown) {
                SparkCore.LOGGER.info(entity.getBone("rightLeg").data.rotation.toString())
                entity.animController.setAnimation("hammer:attack_3", 0)
            }
        }
    }

    @SubscribeEvent
    private fun onBoneUpdate(event: BoneUpdateEvent) {
        val animatable = event.animatable
        animatable.onBoneUpdate(event)
    }

}