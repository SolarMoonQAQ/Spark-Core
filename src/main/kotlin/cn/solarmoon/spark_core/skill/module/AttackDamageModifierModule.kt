package cn.solarmoon.spark_core.skill.module

import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent

data class AttackDamageModifierModule(
    val damageMultiply: Float = 1f
) {

    lateinit var skill: SkillInstance

    /**
     * 攻击时取消原版的攻击间隔对攻击的削弱
     */
    val cancelAttackDuration = { event: PlayerGetAttackStrengthEvent ->
        run {
            val entity = event.entity
            if (entity == skill.holder) {
                event.attackStrengthScale = 1f
            }
        }
    }

    /**
     * 攻击时根据技能倍率增加攻击伤害
     */
    val modifyAttackStrength = { event: LivingIncomingDamageEvent ->
        run {
            val entity = event.source.entity ?: return@run
            if (entity == skill.holder && skill.isActive && !entity.level().isClientSide) {
                event.container.newDamage *= damageMultiply.toFloat()
            }
        }
    }

    /**
     * 使能够兼容别的模组的暴击修改，并且把原版跳劈删去
     */
    val playerCriticalHit = { event: CriticalHitEvent ->
        run {
            val entity = event.entity
            if (entity == skill.holder) {
                if (event.vanillaMultiplier == 1.5f) event.isCriticalHit = false
            }
        }
    }

    /**
     * 原版横扫从此再见
     */
    val playerSweep = { event: SweepAttackEvent ->
        run {
            val entity = event.entity
            if (entity == skill.holder) {
                event.isSweeping = false
            }
        }
    }

    fun active(skill: SkillInstance) {
        this.skill = skill
        val entity = skill.holder as? Entity ?: return
        if (!skill.level.isClientSide && !entity.persistentData.getBoolean("SOFregistedADM")) {
            entity.persistentData.putBoolean("SOFregistedADM", true)
            NeoForge.EVENT_BUS.addListener(cancelAttackDuration)
            NeoForge.EVENT_BUS.addListener(modifyAttackStrength)
            NeoForge.EVENT_BUS.addListener(playerCriticalHit)
            NeoForge.EVENT_BUS.addListener(playerSweep)
        }
    }

    fun end() {
        val entity = skill.holder as? Entity ?: return
        if (!skill.level.isClientSide) {
            entity.persistentData.putBoolean("SOFregistedADM", false)
            NeoForge.EVENT_BUS.unregister(cancelAttackDuration)
            NeoForge.EVENT_BUS.unregister(modifyAttackStrength)
            NeoForge.EVENT_BUS.unregister(playerCriticalHit)
            NeoForge.EVENT_BUS.unregister(playerSweep)
        }
    }

    companion object {
        val CODEC: Codec<AttackDamageModifierModule> = RecordCodecBuilder.create {
            it.group(
                Codec.FLOAT.optionalFieldOf("damage_multiply", 1f).forGetter { it.damageMultiply }
            ).apply(it, ::AttackDamageModifierModule)
        }
    }

}