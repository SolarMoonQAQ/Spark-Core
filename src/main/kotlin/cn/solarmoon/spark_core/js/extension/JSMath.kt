package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.JSComponent
import net.minecraft.world.phys.Vec3
import org.graalvm.polyglot.HostAccess

object JSMath: JSComponent() {

    @HostAccess.Export
    fun vec3() = Vec3.ZERO

    @HostAccess.Export
    fun vec3(x: Double, y: Double, z: Double) = Vec3(x, y ,z)

}