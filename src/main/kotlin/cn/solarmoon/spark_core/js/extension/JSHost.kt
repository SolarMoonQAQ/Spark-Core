package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.SparkJS
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.HostAccess

class JSHost(
    val js: SparkJS,
    val entity: Any
) {

    @HostAccess.Export
    fun asEntity() = entity as? Entity

    @HostAccess.Export
    fun asAnimatable() = entity as? IAnimatable<*>

}