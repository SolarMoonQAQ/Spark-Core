package cn.solarmoon.spark_core.animation.state

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.blend.BlendAnimation
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
            if (entity.mainHandItem.item is CrossbowItem && CrossbowItem.isCharged(entity.mainHandItem)) {
                entity.animController.blendSpace.blendAnimMap.remove("poseMixL")
                val animName = "Pose/crossbow_right"
                val origin = entity.animations.getAnimation(animName) ?: return
                entity.animController.mainAnim?.shouldTurnBody = true
                entity.animController.blendSpace.blendAnimMap.putIfAbsent("poseMixR",
                    BlendAnimation(AnimInstance.create(entity, animName, origin), 1000000.0).apply { shouldClearWhenResetAnim = false }
                )
            } else if (entity.mainHandItem.item is CrossbowItem && CrossbowItem.isCharged(entity.offhandItem)) {
                entity.animController.blendSpace.blendAnimMap.remove("poseMixR")
                val animName = "Pose/crossbow_left"
                val origin = entity.animations.getAnimation(animName) ?: return
                entity.animController.mainAnim?.shouldTurnBody = true
                entity.animController.blendSpace.blendAnimMap.putIfAbsent("poseMixL",
                    BlendAnimation(AnimInstance.create(entity, animName, origin), 1000000.0).apply { shouldClearWhenResetAnim = false }
                )
            } else {
                entity.animController.blendSpace.blendAnimMap.remove("poseMixR")
                entity.animController.blendSpace.blendAnimMap.remove("poseMixL")
            }

            if (entity.swinging && entity.swingingArm == InteractionHand.MAIN_HAND && entity.animations.hasAnimation("Pose/swinging_right")) {
                entity.animController.blendSpace.blendAnimMap.putIfAbsent("swingMixR", BlendAnimation(entity.newAnimInstance("Pose/swinging_right"), 100000.0))
            } else entity.animController.blendSpace.blendAnimMap.remove("swingMixR")

            if (entity.swinging && entity.swingingArm == InteractionHand.OFF_HAND && entity.animations.hasAnimation("Pose/swinging_left")) {
                entity.animController.blendSpace.blendAnimMap.putIfAbsent("swingMixL", BlendAnimation(entity.newAnimInstance("Pose/swinging_left"), 100000.0))
            } else entity.animController.blendSpace.blendAnimMap.remove("swingMixL")
        }
    }

}