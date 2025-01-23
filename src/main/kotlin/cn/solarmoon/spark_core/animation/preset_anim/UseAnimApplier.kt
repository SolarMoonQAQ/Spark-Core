package cn.solarmoon.spark_core.animation.preset_anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object UseAnimApplier {

    @SubscribeEvent
    private fun useItem(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*> || entity !is LivingEntity) return

        if (entity.isUsingItem) {
            val item = entity.useItem
            val useAnim = item.useAnimation
            val hand = if (ItemStack.isSameItemSameComponents(item, entity.mainHandItem)) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
            val animName = "UseAnim/${useAnim.name.lowercase()}_${if (hand == InteractionHand.MAIN_HAND) "right" else "left"}"
            val id = "UseAnimMix"
            val origin = entity.animations.getAnimation(animName) ?: return
            entity.animController.blendSpace.putIfAbsent(id,
                BlendAnimation(AnimInstance.create(entity, animName, origin), 1000000.0).apply { shouldClearWhenResetAnim = false }
            )
        } else {
            entity.animController.blendSpace.remove("UseAnimMix")
        }
    }

    @SubscribeEvent
    private fun useItem(event: LivingEntityUseItemEvent.Start) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return

        entity.animController.blendSpace.remove("UseAnimMix")
    }

}