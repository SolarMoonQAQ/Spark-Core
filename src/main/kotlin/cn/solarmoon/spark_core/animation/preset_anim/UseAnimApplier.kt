package cn.solarmoon.spark_core.animation.preset_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.BlendAnimation
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent

object UseAnimApplier {

    @SubscribeEvent
    private fun useItem(event: LivingEntityUseItemEvent.Start) {
        val entity = event.entity
        val item = event.item
        val useAnim = item.useAnimation
        if (entity !is IEntityAnimatable<*>) return

        val hand = if (ItemStack.isSameItemSameComponents(item, entity.mainHandItem)) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        val animName = "UseAnim/${useAnim.name.lowercase()}_${if (hand == InteractionHand.MAIN_HAND) "right" else "left"}"
        val id = "UseAnimMix"
        val origin = entity.animations.getAnimation(animName) ?: return
        entity.animController.blendSpace.putIfAbsent(id,
            BlendAnimation(AnimInstance(entity, animName, origin), 1000000.0).apply { shouldClearWhenResetAnim = false }
        )
    }

    @SubscribeEvent
    private fun tickUseItem(event: LivingEntityUseItemEvent.Tick) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return

        entity.animController.mainAnim?.shouldTurnBody = true
    }

    @SubscribeEvent
    private fun stopUseItem(event: LivingEntityUseItemEvent.Stop) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return
        entity.animController.blendSpace.remove("UseAnimMix")
    }

    @SubscribeEvent
    private fun finishUseItem(event: LivingEntityUseItemEvent.Finish) {
        val entity = event.entity
        if (entity !is IEntityAnimatable<*>) return
        entity.animController.blendSpace.remove("UseAnimMix")
    }

}