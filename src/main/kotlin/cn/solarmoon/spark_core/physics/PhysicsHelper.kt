package cn.solarmoon.spark_core.physics

import com.jme3.bounding.BoundingBox
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import com.jme3.system.JmeSystem
import com.jme3.system.Platform.Os.*
import com.simsilica.mathd.Vec3d
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import org.joml.Matrix4f

fun Vec3.toBVector3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

fun Vec3.toVec3d() = Vec3d(x, y, z)

fun Vec3d.toVec3() = Vec3(x, y, z)

fun org.joml.Vector3f.toBVector3f() = Vector3f(x, y ,z)

fun org.joml.Matrix3f.toBMatrix3f() = Matrix3f(
    m00, m01, m02,
    m10, m11, m12,
    m20, m21, m22
)

fun Matrix3f.toMatrix3f() = org.joml.Matrix3f(
    get(0, 0), get(0, 1), get(0, 2),
    get(1, 0), get(1, 1), get(1, 2),
    get(2, 0), get(2, 1), get(2, 2)
)

/** 复用给 [absoluteAabb] 当零平移用；native 只读它，不会被改写 */
private val ZERO_TRANSLATION = Vector3f()

/**
 * 求碰撞对象在世界坐标下的 AABB，其中**绝对位置由调用方以双精度提供**。
 *
 * 做法：把形状的 AABB 在**原点附近**求得（旋转照传，平移传 0），此时数值只有物体尺寸量级，
 * float 足够精确（误差 ≈ 1e-6 格）；再把双精度的绝对位置叠加到中心的三个分量上。
 *
 * 反过来直接把大坐标交给 `getAabb` 是不行的：它的 JNI 签名
 * （`../Libbulletjme/src/main/native/glue/com_jme3_bullet_collision_shapes_CollisionShape.cpp`）
 * 入参出参**全是 float**，位置会被先窄化成 float。3.0e7 格处 float 间距是 2 格，
 * 于是中心与尺寸双双被量化到 2 格栅格上 —— 这正是远处「地形不激活 / 掉出世界」的根源。
 *
 * 关于旋转：它只决定「原点附近」那一段局部盒的形状，误差贡献 ≈ 物体尺寸 × float 相对精度
 * ≈ 1e-6 格，比碰撞 margin（0.04）和方块粒度（1.0）小四五个数量级；而且 native 侧的
 * `Matrix3f` 参数本来就只接受 float，双精度姿态到了边界同样会被窄化。因此这里不需要 Dp 姿态。
 *
 * @param location 双精度绝对位置（通常来自 `PhysicsCollisionObject.getPhysicsLocationDp`）
 * @param rotationScratch 复用的旋转矩阵临时对象
 * @param aabbScratch 复用的局部 AABB 临时对象
 * @param centerScratch 复用的中心向量临时对象
 */
fun PhysicsCollisionObject.absoluteAabb(
    location: Vec3,
    rotationScratch: Matrix3f,
    aabbScratch: BoundingBox,
    centerScratch: Vector3f
): AABB {
    getPhysicsRotationMatrix(rotationScratch)
    collisionShape.boundingBoxWithoutRecalculate(ZERO_TRANSLATION, rotationScratch, aabbScratch)
    aabbScratch.getCenter(centerScratch)
    val ex = aabbScratch.getXExtent()
    val ey = aabbScratch.getYExtent()
    val ez = aabbScratch.getZExtent()
    return AABB(
        location.x + centerScratch.x - ex,
        location.y + centerScratch.y - ey,
        location.z + centerScratch.z - ez,
        location.x + centerScratch.x + ex,
        location.y + centerScratch.y + ey,
        location.z + centerScratch.z + ez
    )
}