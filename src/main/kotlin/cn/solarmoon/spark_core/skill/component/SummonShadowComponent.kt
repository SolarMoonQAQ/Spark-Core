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
    val color: Int = Color.GRAY.rgb,
    children: List<SkillComponent> = listOf()
): SkillComponent(children) {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return SummonShadowComponent(maxLifeTime, color, children)
    }

    override fun onActive(): Boolean {
        val entity = skill.holder as? Entity ?: return true
        if (!entity.level().isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(entity.id, maxLifeTime, Color(color))
        }
        return true
    }

    override fun onUpdate(): Boolean {
        return true
    }

    override fun onEnd(): Boolean {
        return true
    }

    companion object {
        val CODEC: MapCodec<SummonShadowComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("max_life_time", 20).forGetter { it.maxLifeTime },
                Codec.INT.optionalFieldOf("color", Color.GRAY.rgb).forGetter { it.color },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::SummonShadowComponent)
        }
    }

}