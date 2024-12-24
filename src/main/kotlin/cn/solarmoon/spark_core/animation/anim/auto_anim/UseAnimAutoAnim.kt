package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.UseAnim

open class UseAnimAutoAnim(
    entity: Entity,
    animatable: IEntityAnimatable<*>,
    val hand: InteractionHand
): EntityAutoAnim(entity, animatable, "UseAnim${if (hand == InteractionHand.MAIN_HAND) "R" else "L"}") {

    override val shouldTurnBody: Boolean = true

    override fun getAllAnimNames(): Set<String> {
        return animatable.animData.animationSet.animations.map { it.name }.filter { it.substringBefore("/") == prefix }.toSet()
    }

    override fun isValid(): Boolean {
        if (entity !is LivingEntity) return false
        return !entity.useItem.isEmpty && entity.usedItemHand == hand && entity.useItem.useAnimation != UseAnim.NONE
    }

    override fun getAnimSuffixName(): String {
        val entity = animatable.animatable
        if (entity !is LivingEntity) return ""
        return entity.useItem.useAnimation.toString().lowercase()
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