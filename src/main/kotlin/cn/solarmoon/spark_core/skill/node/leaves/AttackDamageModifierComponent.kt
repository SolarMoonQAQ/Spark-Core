package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent

class AttackDamageModifierComponent(
    val damageMultiply: Float = 1f
): BehaviorNode() {

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return AttackDamageModifierComponent(damageMultiply)
    }

    /**
     * 攻击时取消原版的攻击间隔对攻击的削弱
     */
    @SubscribeEvent
    private fun cancelAttackDuration(event: PlayerGetAttackStrengthEvent) {
        val entity = event.entity
        if (entity == skill.holder) {
            event.attackStrengthScale = 1f
        }
    }

    /**
     * 攻击时根据技能倍率增加攻击伤害
     */
    @SubscribeEvent
    private fun modifyAttackStrength(event: LivingIncomingDamageEvent) {
        val entity = event.source.entity ?: return
        if (entity == skill.holder && skill.isActive && !entity.level().isClientSide) {
            event.container.newDamage *= damageMultiply.toFloat()
        }
    }

    /**
     * 使能够兼容别的模组的暴击修改，并且把原版跳劈删去
     */
    @SubscribeEvent
    private fun playerCriticalHit(event: CriticalHitEvent) {
        val entity = event.entity
        if (entity == skill.holder) {
            if (event.vanillaMultiplier == 1.5f) event.isCriticalHit = false
        }
    }

    /**
     * 原版横扫从此再见
     */
    @SubscribeEvent
    private fun playerSweep(event: SweepAttackEvent) {
        val entity = event.entity
        if (entity == skill.holder) {
            event.isSweeping = false
        }
    }

    override fun onStart(skill: SkillInstance) {
        NeoForge.EVENT_BUS.register(this)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return NodeStatus.RUNNING
    }


    override fun onEnd(skill: SkillInstance) {
        NeoForge.EVENT_BUS.unregister(this)
    }

    companion object {
        val CODEC: MapCodec<AttackDamageModifierComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.FLOAT.optionalFieldOf("damage_multiply", 1f).forGetter { it.damageMultiply }
            ).apply(it, ::AttackDamageModifierComponent)
        }
    }

}