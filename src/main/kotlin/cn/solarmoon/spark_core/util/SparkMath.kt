package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.molang.core.value.DoubleValue
import cn.solarmoon.spark_core.molang.core.value.Vector3k
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bounding.BoundingBox
import com.jme3.math.Matrix4f
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector2d
import org.joml.Vector3d
import org.joml.Vector3f
import java.lang.Math
import java.lang.Math.copySign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.withSign

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

fun BlockPos.toVec3() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Quaternionf.copy(): Quaternionf = Quaternionf(this)

fun Vec3.div(value: Double) = Vec3(x / value, y / value, z / value)

fun Vec3.rotLerp(target: Vec3, progress: Double): Vec3 {
    val start = Quaterniond().rotateZYX(z, y, x)
    val end = Quaterniond().rotateZYX(target.z, target.y, target.x)
    val result = start.slerp(end, progress)
    return result.toEuler().toVec3()
}

fun Vec3.setX(x: Double) = Vec3(x, y, z)
fun Vec3.setY(y: Double) = Vec3(x, y, z)
fun Vec3.setZ(z: Double) = Vec3(x, y, z)

/**
 * 在joml库的默认欧拉角转换中，出现了角度突变，推测是奇点位置未考虑导致的，因此此方法对pitch角的算法进行了修正，应当避免了joml库本身的问题
 */
fun Quaterniond.toEuler(): Vector3d {
    val angles = Vector3d()

    // roll (x-axis rotation)
    val sinr_cosp = 2 * (w * x + y * z)
    val cosr_cosp = 1 - 2 * (x * x + y * y)
    angles.x = atan2(sinr_cosp, cosr_cosp)

    // pitch (y-axis rotation)
    val sinp = 2 * (w * y - z * x)
    angles.y = if (abs(sinp) >= 1)
        (Math.PI / 2).withSign(sinp) // use 90 degrees if out of range
    else
        asin(sinp)

    // yaw (z-axis rotation)
    val siny_cosp = 2 * (w * z + x * y)
    val cosy_cosp = 1 - 2 * (y * y + z * z)
    angles.z = atan2(siny_cosp, cosy_cosp)

    return angles
}

fun Quaternionf.toEuler(): Vector3f {
    return Quaterniond(this).toEuler().toVector3f()
}

fun Vector3f.rotLerp(target: Vector3f, progress: Double, dist: Vector3f): Vector3f {
    return dist.set(toVec3().rotLerp(target.toVec3(), progress).toVector3f())
}

operator fun Vec3.plus(v: Vec3) = add(v)

operator fun Vec3.minus(v: Vec3) = subtract(v)

fun DoubleArray.toVec3() = Vec3(get(0), get(1), get(2))

fun List<Double>.toVec3() = Vec3(get(0), get(1), get(2))

fun Vector3f.rotLerp(target: Vector3f, progress: Double): Vector3f {
    return rotLerp(target, progress, this)
}

fun Vector3f.toQuaternionf() = Quaternionf().rotateXYZ(x, y, z)

fun Vector3f.toVec3() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Vector3d.toVec3() = Vec3(x, y, z)

fun Vec3.toVec3i() = Vec3i(x.toInt(), y.toInt(), z.toInt())

fun Vector3d.toVector3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

fun Double.toDegrees() = Math.toDegrees(this)

fun Vec2.toVector2d() = Vector2d(x.toDouble(), y.toDouble())

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

fun Vector3f.toRadians() = Vector3f(
    x.toRadians(),
    y.toRadians(),
    z.toRadians()
)

fun Vec3.wrapDegrees() = Vec3(
    Mth.wrapDegrees(x),
    Mth.wrapDegrees(y),
    Mth.wrapDegrees(z)
)

fun Vector3f.wrapDegrees(dist: Vector3f) = dist.set(
    Mth.wrapDegrees(x),
    Mth.wrapDegrees(y),
    Mth.wrapDegrees(z)
)

fun Vector3f.wrapDegrees() = wrapDegrees(this)

fun com.jme3.math.Vector3f.toVector3f() = Vector3f(x, y, z)

fun com.jme3.math.Vector3f.toVec3() = toVector3f().toVec3()

fun Vec3.toChunkPos() = ChunkPos(x.toInt() shr 4, z.toInt() shr 4)

fun com.jme3.math.Vector3f.toChunkPos() = ChunkPos(x.toInt() shr 4, z.toInt() shr 4)

fun Vector3f.toChunkPos() = ChunkPos(x.toInt() shr 4, z.toInt() shr 4)

fun Vec3.toVector3k() = Vector3k(
    DoubleValue(x),
    DoubleValue(y),
    DoubleValue(z)
)

fun Vector3d.toVector3k() = Vector3k(
    DoubleValue(x),
    DoubleValue(y),
    DoubleValue(z)
)

fun Matrix4f.toMatrix4f() = org.joml.Matrix4f(
    m00, m10, m20, m30,
    m01, m11, m21, m31,
    m02, m12, m22, m32,
    m03, m13, m23, m33
)

fun Matrix3f.copy() = Matrix3f(
    m00, m10, m20,
    m01, m11, m21,
    m02, m12, m22
)

fun Quaternion.toQuaternionf() = Quaternionf(x, y, z, w)

fun Quaternionf.toBQuaternion() = Quaternion(x, y, z, w)

fun Transform.lerp(target: Transform, delta: Float) = Transform(
    translation.toVector3f().lerp(target.translation.toVector3f(), delta).toBVector3f(),
    rotation.toQuaternionf().slerp(target.rotation.toQuaternionf(), delta).toBQuaternion(),
    scale.toVector3f().lerp(target.scale.toVector3f(), delta).toBVector3f()
)

fun catmullromVector3f(prev: Vector3f, now: Vector3f, next: Vector3f, nextNext: Vector3f, t: Float): Vector3f {
    return Vector3f(
        Mth.catmullrom(t, prev.x, now.x, next.x, nextNext.x),
        Mth.catmullrom(t, prev.y, now.y, next.y, nextNext.y),
        Mth.catmullrom(t, prev.z, now.z, next.z, nextNext.z)
    )
}

fun catmullromVec3(prev: Vec3, now: Vec3, next: Vec3, nextNext: Vec3, t: Float): Vec3 {
    return catmullromVector3f(prev.toVector3f(), now.toVector3f(), next.toVector3f(), nextNext.toVector3f(), t).toVec3()
}

/**
 * 将JME的BoundingBox转换为Minecraft的AABB
 */
fun BoundingBox.toAABB(): AABB {
    val min = this.getMin(null).toVec3()
    val max = this.getMax(null).toVec3()
    return AABB(min, max)
}