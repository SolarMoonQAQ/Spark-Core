package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.neoforged.bus.api.Event

abstract class AutoAnimRegisterEvent(
    protected open val animatable: IAnimatable<*>,
    protected val allAutoAnims: MutableSet<Lazy<AutoAnim<*>>>
): Event() {

    class Entity(
        override val animatable: IEntityAnimatable<*>,
        private val entity: net.minecraft.world.entity.Entity,
        allAutoAnims: MutableSet<Lazy<AutoAnim<*>>>
    ): AutoAnimRegisterEvent(animatable, allAutoAnims) {

        fun register(anim: (net.minecraft.world.entity.Entity, IEntityAnimatable<*>) -> EntityAutoAnim) {
            allAutoAnims.add(lazy { anim.invoke(entity, animatable) })
        }

    }

}