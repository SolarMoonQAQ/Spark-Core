package cn.solarmoon.spark_core.skill.condition

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillHost
import com.mojang.serialization.MapCodec
import net.minecraft.resources.ResourceLocation
import java.util.function.Function

interface SkillCondition {

    val registryKey: ResourceLocation get() = SparkRegistries.SKILL_CONDITION_CODEC.getKey(codec) ?: throw NullPointerException("技能条件尚未注册")

    val codec: MapCodec<out SkillCondition>

    fun test(holder: SkillHost): Boolean

    companion object {
        val CODEC = SparkRegistries.SKILL_CONDITION_CODEC.byNameCodec()
            .dispatch(
                SkillCondition::codec,
                Function.identity()
            )
    }

}