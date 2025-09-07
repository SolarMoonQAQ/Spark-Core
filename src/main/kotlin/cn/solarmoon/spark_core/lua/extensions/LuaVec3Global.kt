package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.lua.doc.LuaGlobal
import net.minecraft.world.phys.Vec3

@LuaGlobal("Vec3")
object LuaVec3Global {

    fun create(x: Double, y: Double, z: Double): Vec3 {
        return Vec3(x, y, z)
    }

}