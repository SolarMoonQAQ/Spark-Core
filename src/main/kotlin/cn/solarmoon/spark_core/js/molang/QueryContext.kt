package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.IAnimatable
import net.minecraft.world.entity.Entity

class QueryContext(
    val animatable: IAnimatable<*>
) {

    @JvmField
    val ground_speed = animatable.animLevel?.dayTime?.toDouble() ?: 0.0

    fun getTags(any: Any) = animatable.animLevel

}