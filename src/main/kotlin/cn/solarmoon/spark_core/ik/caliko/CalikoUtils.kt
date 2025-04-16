package cn.solarmoon.spark_core.ik.caliko

import au.edu.federation.utils.Vec3f
import org.joml.Vector3f
import net.minecraft.world.phys.Vec3
/**
 * Conversion utilities between Spark/Minecraft types and Caliko types.
 */
object CalikoUtils {
    /** Converts a Joml Vector3f to a Caliko Vec3f. */
    fun Vector3f.toCalikoVec3f(): Vec3f {
        return Vec3f(this.x, this.y, this.z)
    }

    /** Converts a Caliko Vec3f to a Joml Vector3f. */
    fun Vec3f.toJomlVector3f(): Vector3f {
        return Vector3f(this.x, this.y, this.z)
    }
    fun Vec3.toCalikoVec3f(): Vec3f {
        return Vec3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
    }
}
