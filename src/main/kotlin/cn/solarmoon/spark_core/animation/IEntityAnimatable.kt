package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.ModelData
import cn.solarmoon.spark_core.animation.sync.SetAnimPayload
import cn.solarmoon.spark_core.entity.state.getCommonAnimStateMachine
import cn.solarmoon.spark_core.phys.toRadians
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.registry.common.SparkEntityStates
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.PI


interface IEntityAnimatable<T: Entity>: IAnimatable<T> {

    override var modelData: ModelData
        get() = animatable.getData(SparkAttachments.ANIM_DATA)
        set(value) { animatable.setData(SparkAttachments.ANIM_DATA, value) }

    override fun getWorldPosition(partialTick: Float): Vec3 {
        return animatable.getPosition(partialTick)
    }

    override fun getRootYRot(partialTick: Float): Float {
        return PI.toFloat() - animatable.getPreciseBodyRotation(partialTick).toRadians()
    }

    fun syncMainAnimToClient() {
        animController.getPlayingAnim()?.let {
            PacketDistributor.sendToAllPlayers(SetAnimPayload(animatable.id, it.name, animController.transitionTick, it.speed, it.shouldTurnBody))
        }
    }

}