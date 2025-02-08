package cn.solarmoon.spark_core.skill.component.collision

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillInstance
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.mojang.serialization.MapCodec
import java.util.function.Function

interface CollisionComponent {

    val codec: MapCodec<out CollisionComponent>

    fun apply(skill: SkillInstance, body: PhysicsCollisionObject)

    companion object {
        val CODEC = SparkRegistries.COLLISION_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                CollisionComponent::codec,
                Function.identity()
            )
    }

}