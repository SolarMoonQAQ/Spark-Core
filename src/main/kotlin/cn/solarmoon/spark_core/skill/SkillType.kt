package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.RegistryAccess
import net.minecraft.world.level.Level

class SkillType(
    val components: List<SkillComponent>,
    val flags: Set<String> = setOf()
) {

    fun getRegistryKey(access: RegistryAccess) = access.registryOrThrow(SparkRegistries.SKILL_TYPE).getKey(this) ?: throw NullPointerException("技能类型尚未注册")

    fun createSkill(holder: SkillHost, level: Level): SkillInstance {
        val result = SkillInstance(this, holder, level, this@SkillType.components.map { it.copy() })
        return result
    }

    companion object {
        val CODEC: Codec<SkillType> = RecordCodecBuilder.create {
            it.group(
                SkillComponent.Companion.CODEC.listOf().fieldOf("components").forGetter { it.components },
                Codec.STRING.listOf().xmap({ it.toSet() }, { it.toList() }).optionalFieldOf("flags", setOf()).forGetter { it.flags }
            ).apply(it, ::SkillType)
        }
    }

}