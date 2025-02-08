package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.condition.SkillCondition
import cn.solarmoon.spark_core.sync.SyncerType
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceLocation
import java.util.function.Function

class SkillGroup(
    val priority: Int,
    val binders: Map<SyncerType, Set<ResourceLocation>>,
    val conditions: List<SkillCondition>,
    val controllers: List<SkillGroupController>
) {

    fun getRegistryKey(access: RegistryAccess) = access.registryOrThrow(SparkRegistries.SKILL_GROUP).getKey(this) ?: throw NullPointerException("技能组尚未注册！")

    fun checkConditions(holder: SkillHost) = conditions.all { it.test(holder) }

    companion object {
        val CODEC: Codec<SkillGroup> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.optionalFieldOf("priority", 0).forGetter { it.priority },
                Codec.unboundedMap(SparkRegistries.SYNCER_TYPE.byNameCodec(), ResourceLocation.CODEC.listOf().xmap({it.toSet()}, { it.toMutableList() })).fieldOf("binders").forGetter { it.binders },
                SkillCondition.CODEC.listOf().fieldOf("conditions").forGetter { it.conditions },
                SkillGroupController.CODEC.listOf().fieldOf("controllers").forGetter { it.controllers }
            ).apply(it, ::SkillGroup)
        }
    }

}