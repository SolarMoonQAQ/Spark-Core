package cn.solarmoon.spark_core.skill.component.particle

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.Skill
import com.mojang.serialization.MapCodec
import java.util.function.Function

interface ParticleComponent {

    fun tick(skill: Skill)

    val codec: MapCodec<ParticleComponent>

    companion object {
        val CODEC = SparkRegistries.PARTICLE_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                ParticleComponent::codec,
                Function.identity()
            )
    }

}