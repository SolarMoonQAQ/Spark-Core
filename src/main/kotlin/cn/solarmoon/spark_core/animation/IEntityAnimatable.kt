package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.physics.body.RigidBodyEntity
import cn.solarmoon.spark_core.registry.common.SparkSyncerTypes
import cn.solarmoon.spark_core.sync.IntSyncData
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.util.toRadians
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import kotlin.math.PI


interface IEntityAnimatable<T: Entity>: IAnimatable<T> {

    override fun getWorldPositionMatrix(partialTicks: Number): Matrix4f {
        return Matrix4f()
            .translate(animatable.getPosition(partialTicks.toFloat()).toVector3f())
            .rotateY(PI.toFloat() - animatable.getPreciseBodyRotation(partialTicks.toFloat()).toRadians())
    }

    override val animLevel: Level
        get() = animatable.level()

}