package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.skill.Skill
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import java.awt.Color

data class SummonShadowComponent(
    val maxLifeTime: Int = 20,
    val color: Int = Color.GRAY.rgb
): SkillComponent() {

    override fun onAttach() {
        val entity = skill.holder as? Entity ?: return
        if (!entity.level().isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(entity.id, maxLifeTime, Color(color))
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<SummonShadowComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("max_life_time", 20).forGetter { it.maxLifeTime },
                Codec.INT.optionalFieldOf("color", Color.GRAY.rgb).forGetter { it.color }
            ).apply(it, ::SummonShadowComponent)
        }
    }

}