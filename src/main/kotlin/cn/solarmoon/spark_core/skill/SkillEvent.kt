package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent

open class SkillEvent {
    class Hurt(val event: LivingIncomingDamageEvent): SkillEvent()
    class TargetHurt(val event: LivingIncomingDamageEvent): SkillEvent()
    class ActualHurt(val event: LivingDamageEvent): SkillEvent()
    class TargetActualHurt(val event: LivingDamageEvent): SkillEvent()
    class KnockBack(val event: LivingKnockBackEvent): SkillEvent()
    class TargetKnockBack(val event: LivingKnockBackEvent): SkillEvent()
    class LocalInputUpdate(val event: MovementInputUpdateEvent): SkillEvent()
    class PlayerGetAttackStrength(val event: PlayerGetAttackStrengthEvent): SkillEvent()
    class CriticalHit(val event: CriticalHitEvent): SkillEvent()
    class SweepAttack(val event: SweepAttackEvent): SkillEvent()
    object WindupStart: SkillEvent()
    object Windup: SkillEvent()
    object ActiveStart: SkillEvent()
    object Active: SkillEvent()
    object RecoveryStart: SkillEvent()
    object Recovery: SkillEvent()
    object End: SkillEvent()
    object PhysicsTick: SkillEvent()
}