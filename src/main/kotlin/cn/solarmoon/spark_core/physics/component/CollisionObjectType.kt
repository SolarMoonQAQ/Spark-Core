package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.physics.component.shape.CollisionShapeType
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.jme3.bullet.collision.shapes.CollisionShape
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

class CollisionObjectType<C: CollisionObjectComponent<*>>(
    val location: ResourceLocation,
    private val provider: CollisionObjectTypeProvider<C>
) {

    fun create(name: String, authority: Authority, level: Level): C {
        return provider.create(name, authority, level).apply { updateData() }
    }

    companion object {
        val STREAM_CODEC = ByteBufCodecs.registry(SparkRegistries.COLLISION_OBJECT_TYPE.key())
    }

}

fun interface CollisionObjectTypeProvider<C: CollisionObjectComponent<*>> {
    fun create(name: String, authority: Authority, level: Level): C
}