package cn.solarmoon.spark_core.api.animation

import cn.solarmoon.spark_core.api.animation.anim.part.Animation
import cn.solarmoon.spark_core.api.animation.sync.AnimNetData
import cn.solarmoon.spark_core.api.phys.toRadians
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI

/**
 * 所有动画模型数据在服务端的生物都需要接入此接口
 *
 * 该接口提供多种方法可以便捷地定位当前生物的模型上的各个点在世界中的位置等等
 */
interface IEntityAnimatable<T: Entity>: IAnimatable<T> {

    /**
     * 在此组中的动画播放时，会强制让生物的yRot逐渐旋转到目视方向
     */
    val turnBodyAnims: List<String> get() = listOf()

    override fun getPositionMatrix(partialTick: Float): Matrix4f {
        return Matrix4f().translate(animatable.getPosition(partialTick).toVector3f()).rotateY(PI.toFloat() - animatable.getPreciseBodyRotation(partialTick).toRadians())
    }

    override fun getBonePivot(name: String, partialTick: Float): Vector3f {
        val ma = getPositionMatrix(partialTick)
        val bone = animData.model.getBone(name)
        bone.applyTransformWithParents(animData.playData, ma, getExtraTransform(partialTick), partialTick)
        val pivot = bone.pivot.toVector3f()
        return ma.transformPosition(pivot)
    }

    override fun getBoneMatrix(name: String, partialTick: Float): Matrix4f {
        val ma = getPositionMatrix(partialTick)
        val bone = animData.model.getBone(name)
        bone.applyTransformWithParents(animData.playData, ma, getExtraTransform(partialTick), partialTick)
        return ma
    }

    /**
     * 默认存在一个自动检测的头部的变换
     */
    override fun getExtraTransform(partialTick: Float): Map<String, Matrix4f> {
        val pitch = -animatable.getViewXRot(partialTick).toRadians()
        val yaw = -animatable.getViewYRot(partialTick).toRadians() + animatable.getPreciseBodyRotation(partialTick).toRadians()
        return mapOf(Pair("head", Matrix4f().rotateZYX(0f, yaw, pitch)))
    }

    override fun syncAnimDataToClient() {
        if (!animatable.level().isClientSide) PacketDistributor.sendToAllPlayers(AnimNetData(animatable.id, animData))
    }

}