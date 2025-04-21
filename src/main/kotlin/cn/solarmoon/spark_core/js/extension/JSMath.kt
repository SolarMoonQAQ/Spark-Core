package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.JSComponent
import net.minecraft.world.phys.Vec3

object JSMath: JSComponent() {

    fun vec3() = Vec3.ZERO

    fun vec3(x: Double, y: Double, z: Double) = Vec3(x, y ,z)

}