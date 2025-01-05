package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

open class UseItemAutoAnim(
    entity: Entity,
    animatable: IEntityAnimatable<*>,
    val hand: InteractionHand
): EntityAutoAnim(entity, animatable, "UseItem${if (hand == InteractionHand.MAIN_HAND) "R" else "L"}") {

    override val shouldTurnBody: Boolean = true

    override fun getAllAnimNames(): Set<String> {
        return animatable.animData.animationSet.animations.keys.filter { it.substringBefore("/") == prefix }.toSet()
    }

    override fun isValid(): Boolean {
        if (entity !is LivingEntity) return false
        return !entity.useItem.isEmpty && entity.usedItemHand == hand
    }

    override fun getAnimSuffixName(): String {
        val entity = animatable.animatable
        if (entity !is LivingEntity) return ""
        return BuiltInRegistries.ITEM.getKey(entity.useItem.item).toString()
    }

    override fun tick() {
        if (entity is IEntityAnimatable<*>) {
            animTrigger = tryPlay { it.startTransSpeed = 4f }

            if (animTrigger) {
                animatable.getAutoAnim<EntityStateAutoAnim>("EntityState")?.blendWithoutArms(false) { it.name != getAnimation().name }
            }
        }
    }

}