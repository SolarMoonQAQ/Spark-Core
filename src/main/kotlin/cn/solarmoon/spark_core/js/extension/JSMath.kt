package cn.solarmoon.spark_core.js.extension

import net.minecraft.world.phys.Vec3
import org.graalvm.polyglot.HostAccess

object JSMath {

    @HostAccess.Export
    fun vec3() = Vec3.ZERO

    @HostAccess.Export
    fun vec3(x: Double, y: Double, z: Double) = Vec3(x, y ,z)

}