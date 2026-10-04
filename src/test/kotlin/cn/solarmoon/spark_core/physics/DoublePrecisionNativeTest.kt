package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.level.PhysicsSpaceBounds
import com.jme3.bounding.BoundingBox
import com.jme3.bullet.CollisionConfiguration
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.SphereCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Matrix3f
import com.jme3.math.Vector3f
import com.jme3.system.JmeSystem
import com.jme3.system.Platform
import com.simsilica.mathd.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * native 档位与双精度坐标管线的探针（决策 D11 的第 3 项）。
 *
 * 运行前提：测试用的模组运行时已经通过 `cn.solarmoon.spark_core.pack.NativeLoader` 加载了 native
 * （模组构造时加载，早于测试执行），因此测试里可以直接调用 [NativeLibrary]。
 *
 * 覆盖两件事：
 * 1. 桌面平台拿到的是**双精度** native，且版本与内嵌 Java 层的 `expectedVersion` 一致；
 * 2. `PhysicsBodyState` 依赖的「在原点附近求 AABB + 双精度绝对位置叠加」在世界边界坐标处不丢精度
 *    —— 这正是 float 路径会失败的地方。
 */
class DoublePrecisionNativeTest {

    private fun isAndroid(): Boolean = JmeSystem.getPlatform().os == Platform.Os.Android

    @Test
    fun `native 版本与内嵌 Java 层的声明一致`() {
        assertEquals(NativeLibrary.expectedVersion, NativeLibrary.versionNumber())
    }

    @Test
    fun `桌面平台加载的是双精度 native`() {
        // 候选表上桌面（Windows / Linux / macOS）只有 Dp 档，落到任何一个候选都应是双精度；
        // Android 目前只有 Sp 构件（bullet_dp 目录留空），因此不在此断言
        assumeTrue(!isAndroid(), "Android 当前投放的是 Sp native，跳过双精度断言")
        assertTrue(NativeLibrary.isDoublePrecision(), "桌面候选（bullet_dpmt / bullet_dp）实测都不是双精度")
    }

    @Test
    fun `双精度位置入口能完整往返超出 float 精度的大坐标`() {
        assumeTrue(NativeLibrary.isDoublePrecision(), "Sp native 下 Dp 入口会被窄化，无法完整往返")

        val body = PhysicsRigidBody(SphereCollisionShape(0.5f), 1f)
        // 12,345,678.3 与 -29,999,984.5 都在 float 的间距 ≥1 的区间里
        val requested = Vec3d(12_345_678.3, 64.25, -29_999_984.5)
        body.setPhysicsLocationDp(requested)

        val actual = body.getPhysicsLocationDp(Vec3d())
        assertEquals(requested.x, actual.x, 1.0e-9, "X 未完整往返")
        assertEquals(requested.y, actual.y, 1.0e-9, "Y 未完整往返")
        assertEquals(requested.z, actual.z, 1.0e-9, "Z 未完整往返")
    }

    @Test
    fun `绝对包围盒在世界边界坐标处既不偏移也不被量化`() {
        assumeTrue(NativeLibrary.isDoublePrecision(), "Sp native 下绝对坐标本身就是 float，无法验证")

        val shape = SphereCollisionShape(0.5f)
        val body = PhysicsRigidBody(shape, 1f)
        // 29,999,984.5：该处 float 间距为 2，float 路径会把 .5 直接吃掉
        val x = 29_999_984.5
        body.setPhysicsLocationDp(Vec3d(x, 100.0, 0.0))

        val location = body.getPhysicsLocationDp(Vec3d()).toVec3()
        val aabb = body.absoluteAabb(location, Matrix3f(), BoundingBox(), Vector3f())

        // 中心必须与双精度位置一致（float 路径在这里会偏掉 0.5 甚至 2 格）
        assertEquals(x, (aabb.minX + aabb.maxX) / 2.0, 1.0e-6, "绝对包围盒中心发生偏移")

        // 尺寸必须等于形状在原点附近的真实尺寸，而不是被量化到 float 栅格
        val localBox = shape.boundingBox(Vector3f(), Matrix3f(), BoundingBox())
        val expectedWidth = localBox.getXExtent().toDouble() * 2.0
        assertEquals(expectedWidth, aabb.maxX - aabb.minX, 1.0e-6, "绝对包围盒尺寸被量化")
    }

    @Test
    fun `按真实世界参数构造的物理空间能让世界边界处的刚体正常落地`() {
        assumeTrue(NativeLibrary.isDoublePrecision(), "Sp native 下绝对坐标本身就是 float，无法验证")

        // 与 PhysicsWorld 完全相同的构造参数：DBVT broadphase + countThreads() 个求解器 + 同一组世界边界。
        // Mt native 下 countThreads()=2，native 侧会走 btDiscreteDynamicsWorldMt + btConstraintSolverPoolMt(2)；
        // 非 Mt native 下返回 1，走单求解器路径（../../../../../../../Libbulletjme/src/main/native/glue/jmePhysicsSpace.cpp）
        val space = PhysicsSpace(
            PhysicsSpaceBounds.min(),
            PhysicsSpaceBounds.max(),
            PhysicsSpace.BroadphaseType.DBVT,
            NativeLibrary.countThreads(),
            CollisionConfiguration(8192, 1)
        )

        // 29,999,984.5：float 间距为 2，float 路径会把这个 .5 直接吃掉
        val x = 29_999_984.5
        val ground = PhysicsRigidBody(BoxCollisionShape(8f, 0.5f, 8f), 0f)
        ground.setPhysicsLocationDp(Vec3d(x, 0.0, 0.0))
        space.addCollisionObject(ground)

        val box = PhysicsRigidBody(BoxCollisionShape(0.5f), 1f)
        box.setPhysicsLocationDp(Vec3d(x, 3.0, 0.0))
        space.addCollisionObject(box)

        repeat(300) { space.update(1f / 60f, 0) }

        val resting = box.getPhysicsLocationDp(Vec3d())
        assertTrue(resting.y in 0.9..1.2, "刚体没有落在静态地面上，实际 Y=${resting.y}")
        assertEquals(x, resting.x, 0.05, "世界边界处的横向坐标发生了漂移")
    }
}
