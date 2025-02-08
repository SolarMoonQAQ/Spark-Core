package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.atomic.AtomicInteger

class SkillType(
    val components: List<SkillComponent>
) {

    fun getRegistryKey(access: RegistryAccess) = access.registryOrThrow(SparkRegistries.SKILL_TYPE).getKey(this) ?: throw NullPointerException("技能类型尚未注册")

    fun createSkill(holder: SkillHost): SkillInstance {
        val result = SkillInstance(this, holder, this@SkillType.components.map { it.copy() })
        return result
    }

    companion object {
        val CODEC: Codec<SkillType> = RecordCodecBuilder.create {
            it.group(
                SkillComponent.CODEC.listOf().fieldOf("components").forGetter { it.components }
            ).apply(it, ::SkillType)
        }
    }

}