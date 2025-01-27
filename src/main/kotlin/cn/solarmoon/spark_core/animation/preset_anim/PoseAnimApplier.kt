package cn.solarmoon.spark_core.animation.preset_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.CrossbowItem
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object PoseAnimApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity is LivingEntity && entity is IEntityAnimatable<*>) {
            if (CrossbowItem.isCharged(entity.mainHandItem)) {
                entity.animController.blendSpace.remove("poseMixL")
                val animName = "Pose/crossbow_right"
                val origin = entity.animations.getAnimation(animName) ?: return
                entity.animController.mainAnim?.shouldTurnBody = true
                entity.animController.blendSpace.putIfAbsent("poseMixR",
                    BlendAnimation(AnimInstance.create(entity, animName, origin), 1000000.0).apply { shouldClearWhenResetAnim = false }
                )
            } else if (CrossbowItem.isCharged(entity.offhandItem)) {
                entity.animController.blendSpace.remove("poseMixR")
                val animName = "Pose/crossbow_left"
                val origin = entity.animations.getAnimation(animName) ?: return
                entity.animController.mainAnim?.shouldTurnBody = true
                entity.animController.blendSpace.putIfAbsent("poseMixL",
                    BlendAnimation(AnimInstance.create(entity, animName, origin), 1000000.0).apply { shouldClearWhenResetAnim = false }
                )
            } else {
                entity.animController.blendSpace.remove("poseMixR")
                entity.animController.blendSpace.remove("poseMixL")
            }

            if (entity.swinging && entity.swingingArm == InteractionHand.MAIN_HAND) {
                entity.animController.blendSpace.putIfAbsent("swingMixR", BlendAnimation(entity.newAnimInstance("Pose/swinging_right") { speed = 2.0 }, 100000.0))
            } else entity.animController.blendSpace.remove("swingMixR")

            if (entity.swinging && entity.swingingArm == InteractionHand.OFF_HAND) {
                entity.animController.blendSpace.putIfAbsent("swingMixL", BlendAnimation(entity.newAnimInstance("Pose/swinging_left") { speed = 2.0 }, 100000.0))
            } else entity.animController.blendSpace.remove("swingMixL")
        }
    }

}