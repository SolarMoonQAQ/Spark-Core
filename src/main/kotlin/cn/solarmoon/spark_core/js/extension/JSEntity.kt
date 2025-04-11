package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.anim.JSAnimatable
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.HostAccess

class JSEntity(
    val js: SparkJS,
    val entity: Any
) {

    @HostAccess.Export
    fun asEntity() = entity as? Entity

    @HostAccess.Export
    fun asAnimatable() = (entity as? IAnimatable<*>)?.let { JSAnimatable(js, it) }

}