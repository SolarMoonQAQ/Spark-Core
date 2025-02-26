package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.knockBackRelative
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.registry.common.SparkSkillContext
import cn.solarmoon.spark_core.skill.payload.SkillComponentPayload
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageSources
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class SelfKnockBackComponent(
    val strength: Double = 1.0,
    val fly: Boolean = true
): SkillComponent() {

    override fun onAttach() {
        val level = skill.level
        if (level.isClientSide) return
        val source = skill.blackBoard.require(SparkSkillContext.DAMAGE_SOURCE, this)
        source.sourcePosition?.let {
            PacketDistributor.sendToAllPlayers(SkillComponentPayload(this, CompoundTag().apply {
                putDouble("x", it.x)
                putDouble("y", it.y)
                putDouble("z", it.z)
            }))
        }
    }

    override fun sync(data: CompoundTag, context: IPayloadContext) {
        val victim = skill.holder as? LivingEntity ?: return
        if (!fly) victim.setOnGround(false)
        victim.knockBackRelative(Vec3(data.getDouble("x"), data.getDouble("y"), data.getDouble("z")), strength)
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<SelfKnockBackComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.DOUBLE.optionalFieldOf("strength", 1.0).forGetter { it.strength },
                Codec.BOOL.optionalFieldOf("fly", true).forGetter { it.fly }
            ).apply(it, ::SelfKnockBackComponent)
        }
    }

}