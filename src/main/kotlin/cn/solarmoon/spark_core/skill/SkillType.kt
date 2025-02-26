package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.payload.SkillPredictPayload
import cn.solarmoon.spark_core.skill.payload.SkillSyncPayload
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor

class SkillType<S: Skill>(
    val skill: S,
    val flags: Set<String> = setOf()
) {

    fun getRegistryKey(access: RegistryAccess) = access.registryOrThrow(SparkRegistries.SKILL_TYPE).getKey(this) ?: throw NullPointerException("技能类型尚未注册")

    fun createSkill(holder: SkillHost, level: Level, active: Boolean = false): Skill {
        var result: Skill
        if (level.isClientSide) {
            val id = holder.skillCount.decrementAndGet()
            result = skill.new(id, this, holder, level)
            holder.predictedSkills[id] = result
            PacketDistributor.sendToServer(SkillPredictPayload(holder, this, id, active))
        } else {
            val id = holder.skillCount.incrementAndGet()
            result = skill.new(id, this, holder, level)
            holder.allSkills[id] = result
            PacketDistributor.sendToAllPlayers(SkillSyncPayload(holder, this, id, active))
        }
        if (active) result.activate()
        return result
    }

    companion object {
        val CODEC: Codec<SkillType<*>> = RecordCodecBuilder.create {
            it.group(
                Skill.CODEC.fieldOf("skill").forGetter { it.skill },
                Codec.STRING.listOf().xmap({ it.toSet() }, { it.toList() }).optionalFieldOf("flags", setOf()).forGetter { it.flags }
            ).apply(it, ::SkillType)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SkillType<*>> = ByteBufCodecs.registry(SparkRegistries.SKILL_TYPE)
    }

}