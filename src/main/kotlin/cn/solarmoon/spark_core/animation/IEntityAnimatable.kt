package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI


interface IEntityAnimatable<T: Entity>: IAnimatable<T> {

    override var modelIndex: ModelIndex
        get() = animatable.getData(SparkAttachments.MODEL_INDEX)
        set(value) { animatable.setData(SparkAttachments.MODEL_INDEX, value) }

    override fun getWorldPosition(partialTick: Float): Vec3 {
        return animatable.getPosition(partialTick)
    }

    override fun getRootYRot(partialTick: Float): Float {
        return PI.toFloat() - animatable.getPreciseBodyRotation(partialTick).toRadians()
    }

}