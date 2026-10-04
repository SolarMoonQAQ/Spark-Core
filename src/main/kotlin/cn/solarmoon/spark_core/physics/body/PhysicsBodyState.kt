package cn.solarmoon.spark_core.physics.body

import cn.solarmoon.spark_core.physics.absoluteAabb
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.bounding.BoundingBox
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Matrix3f
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import com.simsilica.mathd.Vec3d
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 刚体的每 tick 状态缓存。
 *
 * 这里有两套坐标精度，消费方按用途各取所需（决策 D4）：
 * - [transform] / [lastTransform] / [cachedBoundingBox] 是 **float**，保留给渲染与下游模组的既有消费方；
 * - [absoluteLocation] 与 [absoluteAabb] 是**双精度绝对坐标**，给依赖绝对位置正确性的消费方
 *   （最典型的是地形构建/激活盒：3.0e7 格处 float 会把盒子量化到 2 格栅格上）。
 *
 * [update] 由物理线程空闲时的主线程调用（见 `PhysicsLevel.requestStep`），因此这里不是热并发路径。
 */
class PhysicsBodyState(
    private val body: PhysicsCollisionObject
) {

    // 复用的临时对象，避免每 tick 为读取双精度状态产生额外垃圾。
    // 必须声明在下面用到 dpScratch 的绝对位置字段之前：Kotlin 按声明顺序初始化属性。
    private val dpScratch = Vec3d()
    private val rotationScratch = Matrix3f()
    private val localAabbScratch = BoundingBox()
    private val centerScratch = Vector3f()

    var lastTransform = body.getTransform(null)// 上一帧的刚体变换，用于插值
    var transform = body.getTransform(null)// 最新的刚体变换
    var cachedBoundingBox = body.boundingBox(null)// 缓存的刚体包围盒，主线程需要获取刚体包围盒时，可从此获取避免竞态和重复计算

    /**
     * 双精度绝对位置。
     *
     * 每 tick 更新时会新建一个 [Vec3]（[Vec3] 不可变，与 [transform] 的语义一致：
     * 消费方拿到的是这一 tick 的快照，不会被后续更新改写）。
     */
    var absoluteLocation: Vec3 = body.getPhysicsLocationDp(dpScratch).toVec3()
        private set

    fun update() {
        lastTransform = transform
        transform = body.getTransform(null)
        cachedBoundingBox = body.boundingBoxWithoutRecalculate(null)
        absoluteLocation = body.getPhysicsLocationDp(dpScratch).toVec3()
    }

    /**
     * 双精度绝对包围盒，见 `cn.solarmoon.spark_core.physics.absoluteAabb`。
     *
     * 会多做一次 `getAabb` 与一次姿态读取的 JNI 调用，只应在需要绝对包围盒时调用
     * （目前只有地形构建/激活盒），不要每帧无条件调用。
     */
    fun absoluteAabb(): AABB =
        body.absoluteAabb(absoluteLocation, rotationScratch, localAabbScratch, centerScratch)

}
