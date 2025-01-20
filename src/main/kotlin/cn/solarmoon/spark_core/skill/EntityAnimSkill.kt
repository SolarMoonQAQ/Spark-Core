package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.world.entity.Entity

abstract class EntityAnimSkill<E: Entity, T: IEntityAnimatable<out E>>(
    holder: T
): AnimSkill<T>(holder) {

    val entity get() = holder.animatable

}