package cn.solarmoon.spark_core.phys

import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3d
import org.joml.Matrix3f
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.*
import org.ode4j.math.DMatrix3
import org.ode4j.math.DQuaternion
import org.ode4j.math.DQuaternionC
import org.ode4j.math.DVector3
import org.ode4j.math.DVector3C
import org.ode4j.ode.DAABB
import org.ode4j.ode.DAABBC
import org.ode4j.ode.DSpace
import org.ode4j.ode.OdeHelper
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3d
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3f

fun Vec3.toRadians(): Vec3 {
    return Vec3(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z))
}

fun Vector3f.copy(): Vector3f = Vector3f(this)

fun Float.toRadians(): Float {
    return Math.toRadians(this.toDouble()).toFloat()
}

fun Double.toRadians(): Double {
    return Math.toRadians(this)
}

fun Float.toDegrees(): Float {
    return Math.toDegrees(this.toDouble()).toFloat()
}

fun Quaternionf.getScaledAxisX(): Vector3f {
    val matrix = Matrix3f().rotate(this)
    return Vector3f(matrix.m00, matrix.m01, matrix.m02)
}

fun Quaternionf.getScaledAxisY(): Vector3f {
    val matrix = Matrix3f().rotate(this)
    return Vector3f(matrix.m10, matrix.m11, matrix.m12)
}

fun Quaternionf.getScaledAxisZ(): Vector3f {
    val matrix = Matrix3f().rotate(this)
    return Vector3f(matrix.m20, matrix.m21, matrix.m22)
}

fun Quaternionf.copy(): Quaternionf = Quaternionf(this)

fun Vec3.toDVector3() = DVector3(x, y, z)

fun Vector3d.toDVector3() = DVector3(x, y, z)

fun Vector3f.toDVector3() = DVector3(x.toDouble(), y.toDouble(), z.toDouble())

fun Quaterniond.toDQuaternion() = DQuaternion(w, x, y, z)

fun DQuaternionC.toQuaternionf() = Quaternionf(get1(), get2(), get3(), get0())

fun DQuaternionC.toQuaterniond() = Quaterniond(get1(), get2(), get3(), get0())

fun Matrix3d.toDMatrix3() = DMatrix3(m00, m01, m02, m10, m11, m12, m20, m21, m22)

fun Matrix3f.toDMatrix3() = Matrix3d(this).toDMatrix3()

fun DVector3C.toVector3d() = Vector3d(get0(), get1(), get2())

fun DVector3C.toVec3() = Vec3(get0(), get1(), get2())

fun DVector3C.toVector3f() = toVector3d().toVector3f()

fun Vector3d.toRotationMatrix() = Matrix3d().rotateXYZ(x, y, z)

fun Vec3.toRotationMatrix() = Matrix3d().rotateXYZ(x, y, z)

fun AABB.toDAABB() = DAABB(minX, maxX, minY, maxY, minZ, maxZ)

fun DAABBC.toDBox(space: DSpace) = OdeHelper.createBox(space, lengths).apply { position = center }

fun Vec3.rotLerp(target: Vec3, progress: Double): Vec3 {
    val start = Quaterniond().rotateZYX(z, y, x)
    val end = Quaterniond().rotateZYX(target.z, target.y, target.x)
    val result = start.slerp(end, progress)
    return result.toEuler().toVec3()
}

fun Quaterniond.toEuler(): Vector3d {
    val angles = Vector3d()
    val q = Quaterniond(this)
    q.normalize()

    // roll (x-axis rotation)
    val sinr_cosp = 2 * (q.w * q.x + q.y * q.z)
    val cosr_cosp = 1 - 2 * (q.x * q.x + q.y * q.y)
    angles.apply { x = atan2(sinr_cosp, cosr_cosp) }

    // pitch (y-axis rotation)
    val sinp = sqrt(1 + 2 * (q.w * q.y - q.x * q.z))
    val cosp = sqrt(1 - 2 * (q.w * q.y - q.x * q.z))
    angles.apply { y = 2 * atan2(sinp, cosp) - PI / 2 }

    // yaw (z-axis rotation)
    val siny_cosp = 2 * (q.w * q.z + q.x * q.y)
    val cosy_cosp = 1 - 2 * (q.y * q.y + q.z * q.z)
    angles.apply { z = atan2(siny_cosp, cosy_cosp) }

    return angles;
}

fun Double.toDegrees() = Math.toDegrees(this)

fun Vec3.toDegrees() = Vec3(
    x.toDegrees(),
    y.toDegrees(),
    z.toDegrees()
)

fun Vector3f.toDegrees() = Vector3f(
    x.toDegrees(),
    y.toDegrees(),
    z.toDegrees()
)

fun Vec3.wrapDegrees() = Vec3(
    Mth.wrapDegrees(x),
    Mth.wrapDegrees(y),
    Mth.wrapDegrees(z)
)