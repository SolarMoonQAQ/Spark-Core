package cn.solarmoon.spark_core.ik.util

import au.edu.federation.utils.Vec3f
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * Conversion utilities between Spark/Minecraft types and Caliko types.
 */
object CalikoUtils {
    /** Converts a Joml Vector3f to a Caliko Vec3f.
     * 注意：反转所有坐标轴以解决Minecraft和Caliko坐标系不兼容的问题
     */
    fun Vector3f.toCalikoVec3f(): Vec3f {
        return Vec3f(-this.x, -this.y, -this.z)
    }

    /** Converts a Minecraft Vec3 to a Caliko Vec3f.
     * 注意：反转所有坐标轴以解决Minecraft和Caliko坐标系不兼容的问题
     */
    fun Vec3.toCalikoVec3f(): Vec3f {
        return Vec3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
    }

    /** Converts a Caliko Vec3f back to Minecraft Vec3.
     * 注意：反转所有坐标轴以保持一致性
     */
    fun Vec3f.toMinecraftVec3(): Vec3 {
        return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
    }
}