package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.jme3.bullet.collision.shapes.CollisionShape
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

class CollisionObjectType<C: CollisionObjectComponent<*>>(
    val location: ResourceLocation,
    private val provider: (String, Authority, CollisionShape, Level) -> C
) {

    fun create(name: String, authority: Authority, shape: CollisionShape, level: Level): C {
        return provider.invoke(name, authority, shape, level)
    }

    companion object {
        val STREAM_CODEC = ByteBufCodecs.registry(SparkRegistries.COLLISION_OBJECT_TYPE.key())
    }

}