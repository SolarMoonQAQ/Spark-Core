package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.handling.IPayloadContext
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