package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.knockBackRelative
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

data class KnockBackComponent(
    val strength: Double = 1.0,
    val fly: Boolean = true
) {

    fun active(victim: LivingEntity, attackerPos: Vec3) {
        if (!fly) victim.setOnGround(false)
        victim.knockBackRelative(attackerPos, strength)
    }

    companion object {
        val CODEC: Codec<KnockBackComponent> = RecordCodecBuilder.create {
            it.group(
                Codec.DOUBLE.optionalFieldOf("strength", 1.0).forGetter { it.strength },
                Codec.BOOL.optionalFieldOf("fly", true).forGetter { it.fly }
            ).apply(it, ::KnockBackComponent)
        }
    }

}