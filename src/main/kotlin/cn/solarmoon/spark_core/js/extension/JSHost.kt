package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.SparkJS
import net.minecraft.world.entity.Entity

class JSHost(
    val js: SparkJS,
    val entity: Any
) {

    fun asEntity() = entity as? Entity

    fun asAnimatable() = entity as? IAnimatable<*>

}