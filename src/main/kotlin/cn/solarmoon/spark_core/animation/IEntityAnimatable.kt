package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.event.ModelIndexChangeEvent
import cn.solarmoon.spark_core.ik.component.IKManager
import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.registry.common.SyncerTypes
import cn.solarmoon.spark_core.sync.IntSyncData
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import kotlin.math.PI


interface IEntityAnimatable<T: Entity>: IAnimatable<T> {

    override var modelIndex: ModelIndex
        get() = animatable.getData(SparkAttachments.MODEL_INDEX)
        set(value) {
            val oldValue = modelIndex
            animatable.setData(SparkAttachments.MODEL_INDEX, value)
            NeoForge.EVENT_BUS.post(ModelIndexChangeEvent(this, oldValue, value))
        }

    override fun getWorldPosition(partialTick: Float): Vec3 {
        return animatable.getPosition(partialTick)
    }

    override fun getRootYRot(partialTick: Float): Float {
        return PI.toFloat() - animatable.getPreciseBodyRotation(partialTick).toRadians()
    }

    override val animLevel: Level
        get() = animatable.level()

    override val syncData: SyncData
        get() = IntSyncData(animatable.id)

    override val syncerType: SyncerType
        get() = SyncerTypes.ENTITY.get()

    val ikManager: IKManager
        get() = IKManager(this)
}