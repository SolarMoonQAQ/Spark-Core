package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import java.awt.Color

data class SummonShadowComponent(
    val maxLifeTime: Int = 20,
    val color: Int = Color.GRAY.rgb
) {

    fun active(entity: Entity) {
        if (!entity.level().isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(entity.id, maxLifeTime, Color(color))
        }
    }

    companion object {
        val CODEC: Codec<SummonShadowComponent> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.optionalFieldOf("max_life_time", 20).forGetter { it.maxLifeTime },
                Codec.INT.optionalFieldOf("color", Color.GRAY.rgb).forGetter { it.color }
            ).apply(it, ::SummonShadowComponent)
        }
    }

}