package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.AnimController
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.Bone
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.phys.toEuler
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaterniond
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3f

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
     * 存储动画体指向的模型动画骨骼等索引数据，可以通过改变data里的指向路径以改变模型和动画
     */
    var modelIndex: ModelIndex

    /**
     * 实际可用的骨骼组
     */
    val bones: BoneGroup

    /**
     * 该动画体的原始模型
     */
    val model get() = modelIndex.model

    /**
     * 该动画体的原始动画列表
     */
    val animations get() = modelIndex.animationSet

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
     * 获取指定的骨骼，没有时会创建一个新的
     */
    fun getBone(name: String) = bones.getOrPut(name) { Bone(this, name) }

    /**
     * 获取动画体到其当前世界位置的变换矩阵
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldPositionMatrix(partialTick: Float = 1f) = Matrix4f().translate(getWorldPosition(partialTick).toVector3f()).rotateY(getRootYRot(partialTick))

    /**
     * 获取动画体指定骨骼在世界位置的变换矩阵
     * @param physPartialTick 物理线程客户端的tick时间
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldBoneMatrix(name: String, physPartialTick: Float = 1f, partialTick: Float = 1f): Matrix4f {
        val ma = getWorldPositionMatrix(partialTick)
        val bone = model.getBone(name)
        bone.applyTransformWithParents(bones, ma, physPartialTick)
        return ma
    }

    /**
     * 获取动画体指定骨骼的枢轴点在世界位置上的坐标
     * @param physPartialTick 物理线程客户端的tick时间
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldBonePivot(name: String, physPartialTick: Float = 1f, partialTick: Float = 1f): Vector3f {
        val ma = getWorldBoneMatrix(name, physPartialTick, partialTick)
        val bone = model.getBone(name)
        val pivot = bone.pivot.toVector3f()
        return ma.transformPosition(pivot)
    }

    /**
     * 获取动画体指定骨骼在模型空间的变换矩阵
     * @param physPartialTick 物理线程客户端的tick时间
     */
    fun getSpaceBoneMatrix(name: String, physPartialTick: Float = 1f): Matrix4f {
        val ma = Matrix4f()
        val bone = model.getBone(name)
        bone.applyTransformWithParents(bones, ma, physPartialTick)
        return ma
    }

    /**
     * 根据name索引创建一个新的动画实例
     */
    fun newAnimInstance(name: String, provider: (AnimInstance).() -> Unit = {}) = AnimInstance.create(this, name, provider = provider)

    /**
     * 当任意骨骼被更新后调用，可以在此基础上对骨骼的位移旋转等参数进行调整
     */
    fun onBoneUpdate(event: BoneUpdateEvent) {}

}