package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent

class AttackDamageModifierComponent(
    val damageMultiply: Float = 1f,
    children: List<SkillComponent> = kotlin.collections.listOf()
): SkillComponent(children) {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return AttackDamageModifierComponent(damageMultiply, children)
    }

    /**
     * 攻击时取消原版的攻击间隔对攻击的削弱
     */
    @SubscribeEvent
    private fun cancelAttackDuration(event: PlayerGetAttackStrengthEvent) {
        event.attackStrengthScale = 1f
    }

    /**
     * 攻击时根据技能倍率增加攻击伤害
     */
    @SubscribeEvent
    private fun modifyAttackStrength(event: LivingIncomingDamageEvent) {
        event.container.newDamage *= damageMultiply.toFloat()
    }

    /**
     * 使能够兼容别的模组的暴击修改，并且把原版跳劈删去
     */
    @SubscribeEvent
    private fun playerCriticalHit(event: CriticalHitEvent) {
        if (event.vanillaMultiplier == 1.5f) event.isCriticalHit = false
    }

    /**
     * 原版横扫从此再见
     */
    @SubscribeEvent
    private fun playerSweep(event: SweepAttackEvent) {
        event.isSweeping = false
    }

    override fun onActive(): Boolean {
        NeoForge.EVENT_BUS.register(this)
        return true
    }

    override fun onUpdate(): Boolean {
        return true
    }

    override fun onEnd(): Boolean {
        NeoForge.EVENT_BUS.unregister(this)
        return true
    }

    companion object {
        val CODEC: MapCodec<AttackDamageModifierComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.FLOAT.optionalFieldOf("damage_multiply", 1f).forGetter { it.damageMultiply },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::AttackDamageModifierComponent)
        }
    }

}