package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import java.awt.Color

class SummonShadowComponent(
    val maxLifeTime: Int = 20,
    val color: Int = Color.GRAY.rgb
): SkillComponent() {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return SummonShadowComponent(maxLifeTime, color)
    }

    override fun onActive() {
        val entity = skill.holder as? Entity ?: return
        if (!entity.level().isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(entity.id, maxLifeTime, Color(color))
        }
    }

    override fun onUpdate() {}

    override fun onEnd() {}

    companion object {
        val CODEC: MapCodec<SummonShadowComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("max_life_time", 20).forGetter { it.maxLifeTime },
                Codec.INT.optionalFieldOf("color", Color.GRAY.rgb).forGetter { it.color }
            ).apply(it, ::SummonShadowComponent)
        }
    }

}