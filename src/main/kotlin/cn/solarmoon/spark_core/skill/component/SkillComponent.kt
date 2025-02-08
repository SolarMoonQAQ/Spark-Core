package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.MapCodec
import net.minecraft.resources.ResourceLocation
import java.util.function.Function

interface SkillComponent {

    val id get() = SparkRegistries.SKILL_COMPONENT_CODEC.getId(codec)

    val registryKey: ResourceLocation get() = SparkRegistries.SKILL_COMPONENT_CODEC.getKey(codec) ?: throw NullPointerException("技能组件尚未注册")

    val codec: MapCodec<out SkillComponent>

    fun copy(): SkillComponent

    fun onActive(skill: SkillInstance): Boolean

    fun onUpdate(skill: SkillInstance): Boolean

    fun onStop(skill: SkillInstance): Boolean

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}