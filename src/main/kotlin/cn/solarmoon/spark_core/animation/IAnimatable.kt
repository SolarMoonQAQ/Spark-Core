package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.AnimController
import cn.solarmoon.spark_core.animation.anim.play.ModelData
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * ### 动画体
 */
interface IAnimatable<T> {

    /**
     * 一般而言输入this即可，用于调用该动画体的持有者
     */
    val animatable: T

    /**
     * 动画控制器，包含了对动画的控制/过渡/混合/获取等控制性操作，在类中新建一个新的即可
     */
    val animController: AnimController

    /**
     * 存储动画体指向的模型动画骨骼等数据，可以通过改变data里的指向路径以改变模型和动画
     */
    var modelData: ModelData

    /**
     * 该动画体的原始模型
     */
    val model get() = modelData.model

    /**
     * 该动画体的原始动画列表
     */
    val animations get() = modelData.animationSet

    /**
     * 动画体所在的世界坐标
     */
    fun getWorldPosition(partialTick: Float = 1f): Vec3

    /**
     * 动画体的yRot（弧度制）
     */
    fun getRootYRot(partialTick: Float = 1f): Float

    /**
     * 获取指定的骨骼
     */
    fun getBone(name: String) = modelData.getBone(name, this)

    /**
     * 获取动画体到其当前世界位置的变换矩阵
     */
    fun getWorldPositionMatrix(partialTick: Float = 1f) = Matrix4f().translate(getWorldPosition(partialTick).toVector3f()).rotateY(getRootYRot(partialTick))

    /**
     * 获取动画体指定骨骼的枢轴点在世界位置的变换矩阵
     */
    fun getWorldBonePivot(name: String, partialTick: Float = 1f): Vector3f {
        val ma = getWorldPositionMatrix(partialTick)
        val bone = model.getBone(name)
        bone.applyTransformWithParents(modelData.bones, ma, partialTick)
        val pivot = bone.pivot.toVector3f()
        return ma.transformPosition(pivot)
    }

    /**
     * 获取动画体指定骨骼在世界位置的变换矩阵
     */
    fun getWorldBoneMatrix(name: String, partialTick: Float = 1f): Matrix4f {
        val ma = getWorldPositionMatrix(partialTick)
        val bone = model.getBone(name)
        bone.applyTransformWithParents(modelData.bones, ma, partialTick)
        return ma
    }

    /**
     * 当任意骨骼被更新后调用，可以在此基础上对骨骼的位移旋转等参数进行调整
     */
    fun onBoneUpdate(event: BoneUpdateEvent) {}

}