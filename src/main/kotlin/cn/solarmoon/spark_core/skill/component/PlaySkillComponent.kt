package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.skill.getSkillType
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

class PlaySkillComponent(
    val skillKey: ResourceLocation
): SkillComponent() {

    override fun onAttach(): Boolean {
        if (skill.level.isClientSide) return false
        getSkillType(skill.level, skillKey).createSkill(skill.holder, skill.level, true)
        return true
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PlaySkillComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                ResourceLocation.CODEC.fieldOf("skill").forGetter { it.skillKey }
            ).apply(it, ::PlaySkillComponent)
        }
    }

}