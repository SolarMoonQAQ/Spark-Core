package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.AnimController
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.Bone
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage
import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.client.Minecraft
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.*

/**
 * ### 动画体
 */
interface IAnimatable<T> : Syncer {

    /**
     * 一般而言输入this即可，用于调用该动画体的持有者
     */
    val animatable: T

    /**
     * 随机数生成器，重写以自行指定随机数种子…吗？
     */
    val randomSeed: Random get() = Random()

    /**
     * 动画提所处的世界
     */
    val level: Level?

    /**
     * 动画控制器，包含了对动画的控制/过渡/混合/获取等控制性操作，在类中新建一个新的即可
     */
    val animController: AnimController

    /**
     * 存储动画体指向的模型动画骨骼等索引数据，可以通过改变data里的指向路径以改变模型和动画
     */
    var modelIndex: ModelIndex

    /**
     * Minecraft客户端实例
     */
    val mc: Minecraft get() = Minecraft.getInstance()

    /**
     * 临时变量存储，用于存储动画结束后自动销毁的临时变量，格式:t.name或temp.name
     */
    val tempStorage: ITempVariableStorage

    /**
     * 作用域变量存储，用于存储动画过程中的变量，格式:v.name或variable.name
     */
    val scopedStorage: IScopedVariableStorage

    /**
     * 外置变量存储，用于存储外部传入的变量，格式:c.name……吗？尚不明确
     */
    val foreignStorage: IForeignVariableStorage

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
     * 获取调用时距离物理线程上一次tick的时间与单tick时间的比值
     */
    val physicsPartialTicks: Float get() = level?.physicsLevel?.partialTicks ?: 1f

    /**
     * 获取调用时距离主线程上一次tick的时间与单tick时间的比值，服务端永远返回1
     */
    val partialTicks: Float get() = if(level?.isClientSide == true) mc.timer.getGameTimeDeltaPartialTick(false) else 1f

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
    fun getWorldPositionMatrix(partialTick: Float = 1f) =
        Matrix4f().translate(getWorldPosition(partialTick).toVector3f()).rotateY(getRootYRot(partialTick))

    /**
     * 获取动画体指定骨骼在世界位置的变换矩阵
     * @param physPartialTick 物理线程客户端的tick时间
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldBoneMatrix(name: String, partialTick: Float = 1f, physPartialTick: Float = 1f): Matrix4f {
        val ma = getWorldPositionMatrix(partialTick)
        val bone = model.getBone(name)
        bone.applyTransformWithParents(bones, ma, partialTick, physPartialTick)
        return ma
    }

    /**
     * 获取动画体指定骨骼的枢轴点在世界位置上的坐标
     * @param physPartialTick 物理线程客户端的tick时间
     * @param partialTick 主线程客户端的tick时间
     */
    fun getWorldBonePivot(name: String, partialTick: Float = 1f, physPartialTick: Float = 1f): Vector3f {
        val ma = getWorldBoneMatrix(name, partialTick, physPartialTick)
        val bone = model.getBone(name)
        val pivot = bone.pivot.toVector3f()
        return ma.transformPosition(pivot)
    }

    /**
     * 获取动画体指定骨骼在模型空间的变换矩阵
     * @param physPartialTick 物理线程客户端的tick时间
     */
    fun getSpaceBoneMatrix(name: String, partialTick: Float = 1f, physPartialTick: Float = 1f): Matrix4f {
        val ma = Matrix4f()
        val bone = model.getBone(name)
        bone.applyTransformWithParents(bones, ma, partialTick, physPartialTick)
        return ma
    }

    /**
     * 获取动画体指定骨骼的枢轴点在模型空间的变换矩阵
     * @param physPartialTick 物理线程客户端的tick时间
     */
    fun getSpaceBonePivot(name: String, partialTick: Float = 1f, physPartialTick: Float = 1f): Vector3f {
        val ma = Matrix4f()
        val bone = model.getBone(name)
        val pivot = bone.pivot.toVector3f()
        bone.applyTransformWithParents(bones, ma, partialTick, physPartialTick)
        return ma.transformPosition(pivot)
    }

    /**
     * 根据name索引创建一个新的动画实例
     */
    fun newAnimInstance(name: String, provider: (AnimInstance).() -> Unit = {}) =
        AnimInstance.create(this, name, provider = provider)

    /**
     * 当任意骨骼被更新后调用，可以在此基础上对骨骼的位移旋转等参数进行调整
     */
    fun onBoneUpdate(event: BoneUpdateEvent) {}

}