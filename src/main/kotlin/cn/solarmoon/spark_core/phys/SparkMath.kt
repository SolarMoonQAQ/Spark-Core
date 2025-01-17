package cn.solarmoon.spark_core.phys

import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3d
import org.joml.Matrix3f
import org.joml.Quaterniond
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

fun Vec3.rotLerp(target: Vec3, progress: Double) = Vec3(
//    rotLerp(progress, x, target.x),
//    rotLerp(progress, y, target.y),
//    rotLerp(progress, z, target.z)
    rotLerp(x, y, z, target, progress)
)

fun rotLerp(progress: Double, start: Double, end: Double): Double {
    return Mth.rotLerp(progress, start, end)
}

fun rotLerp(x: Double, y: Double, z: Double, target: Vec3, progress: Double): Vector3f {
    val start = DQuaternion.fromEulerDegrees(x, y, z)
    val end = DQuaternion.fromEulerDegrees(target.x, target.y, target.z)
    val result = sLerp(start, end, progress)
    return result.toEulerDegrees().toVector3f()
}

fun sLerp(q1: DQuaternion, q2: DQuaternion, t: Double): DQuaternion {
    // 确保输入的四元数是单位四元数
    val normalizedQ1 = q1.normalize()
    var normalizedQ2 = q2.normalize()

    // 计算两个四元数之间的点积
    var cosTheta = normalizedQ1.copy().dot(normalizedQ2)
    // 如果点积接近-1，说明两个四元数指向相反方向，取其中一个的负向进行插值
    if (cosTheta < 0) {
        normalizedQ2 =
            DQuaternion(-normalizedQ2.get0(), -normalizedQ2.get1(), -normalizedQ2.get2(), -normalizedQ2.get3())
        cosTheta = -cosTheta
    }
    if (cosTheta > 1) cosTheta = 1.0 // 确保cosTheta在[0,1]范围内

    // 计算角度
    val theta = acos(cosTheta)

    // 如果角度非常小，接近0，使用线性插值
    if (abs(theta) < 1e-6) {
        val lerp = DQuaternion(
            (1 - t) * normalizedQ1.get0() + t * normalizedQ2.get0(),
            (1 - t) * normalizedQ1.get1() + t * normalizedQ2.get1(),
            (1 - t) * normalizedQ1.get2() + t * normalizedQ2.get2(),
            (1 - t) * normalizedQ1.get3() + t * normalizedQ2.get3()
//            1.0,0.0,0.0,0.0
        )
        return lerp.normalize()
    } else {
        // 使用球面线性插值公式
        val sinTheta = sin(theta)
        val s1 = sin((1 - t) * theta) / sinTheta
        val s2 = sin(t * theta) / sinTheta
        val slerp = DQuaternion(
            s1 * normalizedQ1.get0() + s2 * normalizedQ2.get0(),
            s1 * normalizedQ1.get1() + s2 * normalizedQ2.get1(),
            s1 * normalizedQ1.get2() + s2 * normalizedQ2.get2(),
            s1 * normalizedQ1.get3() + s2 * normalizedQ2.get3()
        )
        return slerp.normalize()
    }
}

fun angleDifference(angle1: Double, angle2: Double): Double {
    val diff = (angle2 - angle1 + 180) % 360 - 180
    return if (diff < -180) diff + 360 else diff
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