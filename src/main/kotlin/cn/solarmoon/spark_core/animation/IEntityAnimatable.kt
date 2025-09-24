package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.registry.common.SparkSyncerTypes
import cn.solarmoon.spark_core.sync.IntSyncData
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.util.toRadians
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI


interface IEntityAnimatable<T: Entity>: IAnimatable<T> {

    override fun getWorldPosition(partialTick: Number): Vec3 {
        return animatable.getPosition(partialTick.toFloat())
    }

    override fun getRootYRot(partialTick: Number): Float {
        return PI.toFloat() - animatable.getPreciseBodyRotation(partialTick.toFloat()).toRadians()
    }

    override val animLevel: Level
        get() = animatable.level()

    override val syncData: SyncData
        get() = IntSyncData(animatable.id)

    override val syncerType: SyncerType
        get() = SparkSyncerTypes.ENTITY.get()

}