package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.payload.SkillPredictPayload
import cn.solarmoon.spark_core.skill.payload.SkillSyncPayload
import io.netty.buffer.ByteBuf
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.collections.set

class SkillType<S: Skill>(
    val registryKey: ResourceLocation,
    val isIndependent: Boolean = true,
    val provider: () -> S,
) {

    internal var fromJS = false
    val name get() = Component.translatable("skill.${registryKey.namespace}.${registryKey.path}.name")
    val description get() = Component.translatable("skill.${registryKey.namespace}.${registryKey.path}.description")

    fun createSkill(holder: SkillHost, level: Level, active: Boolean = false): Skill {
        var result: Skill
        if (level.isClientSide) {
            val id = holder.skillCount.decrementAndGet()
            result = provider().init(id, this, holder, level)
            holder.predictedSkills[id] = result
            PacketDistributor.sendToServer(SkillPredictPayload(holder, this, id, active))
        } else {
            val id = holder.skillCount.incrementAndGet()
            result = provider().init(id, this, holder, level)
            holder.allSkills[id] = result
            PacketDistributor.sendToAllPlayers(SkillSyncPayload(holder, this, id, active))
        }
        if (active) result.activate()
        return result
    }

    fun createSkillWithoutSync(id: Int, holder: SkillHost, level: Level): Skill {
        return provider().init(id, this, holder, level).apply {
            holder.allSkills[id] = this
        }
    }

    companion object {
        val STREAM_CODEC = object : StreamCodec<ByteBuf, SkillType<*>> {
            override fun decode(buffer: ByteBuf): SkillType<*> {
                return SkillManager.values.elementAt(buffer.readInt())
            }

            override fun encode(buffer: ByteBuf, value: SkillType<*>) {
                val index = SkillManager.values.indexOf(value)
                buffer.writeInt(index)
            }

        }
    }

}