package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.world.entity.Entity

abstract class EntityAutoAnim(
    val entity: Entity,
    animatable: IEntityAnimatable<*>,
    prefix: String
): AutoAnim<IEntityAnimatable<*>>(animatable, prefix) {

    abstract val shouldTurnBody: Boolean

    abstract fun tick()

}