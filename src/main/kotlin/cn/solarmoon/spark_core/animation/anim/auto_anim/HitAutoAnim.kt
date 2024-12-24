package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent

abstract class HitAutoAnim(
    entity: Entity,
    animatable: IEntityAnimatable<*>,
    prefix: String
): EntityAutoAnim(entity, animatable, prefix) {

    override val shouldTurnBody: Boolean = false

    override fun isValid(): Boolean {
        return true
    }

    abstract fun onActualHit(event: LivingDamageEvent.Post)

    override fun tick() {}

}