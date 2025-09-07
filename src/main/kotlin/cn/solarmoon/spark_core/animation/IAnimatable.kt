package cn.solarmoon.spark_core.animation

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * ### 动画体
 */
interface IAnimatable<T> : Syncer {

    /**
     * 一般而言输入this即可，用于调用该动画体的持有者
     */
    val animatable: T

    /**
     * 动画体所处的世界
     */
    val animLevel: Level

    /**
     * 动画控制器，包含了对动画的控制/过渡/混合/获取等控制性操作，在类中新建一个新的即可
     */
    val animController: AnimController

    val modelController: ModelController

    /**
     * 动画体所在的世界坐标
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldPosition(partialTick: Float = 1f): Vec3

    /**
     * 动画体的yRot（弧度制）
     * @param partialTick 主线程客户端的tick时间
     */
    fun getRootYRot(partialTick: Float = 1f): Float

    /**
     * 获取动画体到其当前世界位置的变换矩阵
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldPositionMatrix(partialTick: Float = 1f) = Matrix4f().translate(getWorldPosition(partialTick).toVector3f()).rotateY(getRootYRot(partialTick))

    /**
     * 当任意骨骼被更新后调用，可以在此基础上对骨骼的位移旋转等参数进行调整
     */
    fun onBoneUpdate(event: BoneUpdateEvent) {}

    /**
     * 存储当前 IK 链的目标世界坐标。
     * Key: IK 链的名称 (e.g., "left_arm_ik")
     * Value: 目标世界坐标 (Vec3)
     * 这个 Map 应在主线程中更新。建议使用 ConcurrentHashMap 以确保线程安全，尽管最佳实践是主线程写，物理线程读。
     */
    val ikTargetPositions: MutableMap<String, Vec3> // 实现类需要初始化，例如： = ConcurrentHashMap()

    /**
     * 存储已构建的 IK 链实例。
     * Key: IK 链的名称 (e.g., "left_arm_ik")
     * Value: FabrikChain3D 实例
     * 这个 Map 由物理线程或初始化代码填充，并由物理线程读取。建议使用 ConcurrentHashMap。
     */
    val ikChains: MutableMap<String, FabrikChain3D> // 实现类需要初始化，例如： = ConcurrentHashMap()

}