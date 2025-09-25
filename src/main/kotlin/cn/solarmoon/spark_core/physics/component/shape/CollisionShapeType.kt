package cn.solarmoon.spark_core.physics.component.shape

import cn.solarmoon.spark_core.physics.component.CollisionObjectComponent
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.jme3.bullet.collision.shapes.CollisionShape
import net.minecraft.network.codec.ByteBufCodecs

class CollisionShapeType<S: CollisionShape>(
    private val provider: (CollisionObjectComponent<*>) -> S
) {

    fun create(collisionObject: CollisionObjectComponent<*>): S {
        return provider(collisionObject)
    }

    companion object {
        val STREAM_CODEC = ByteBufCodecs.registry(SparkRegistries.COLLISION_SHAPE_TYPE.key())
    }

}