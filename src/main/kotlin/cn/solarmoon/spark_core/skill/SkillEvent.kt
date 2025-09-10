package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

open class SkillEvent {
    class Rejected(val condition: SkillCondition): SkillEvent()
    class Hurt(val event: LivingIncomingDamageEvent): SkillEvent()
    class TargetHurt(val event: LivingIncomingDamageEvent): SkillEvent()
    abstract class ActualHurt: SkillEvent() {
        class Pre(val event: LivingDamageEvent.Pre): ActualHurt()
        class Post(val event: LivingDamageEvent.Post): ActualHurt()
    }
    abstract class TargetActualHurt: SkillEvent() {
        class Pre(val event: LivingDamageEvent.Pre): TargetActualHurt()
        class Post(val event: LivingDamageEvent.Post): TargetActualHurt()
    }
    class KnockBack(val event: LivingKnockBackEvent): SkillEvent()
    class TargetKnockBack(val event: LivingKnockBackEvent): SkillEvent()
    class LocalInputUpdate(val event: MovementInputUpdateEvent): SkillEvent()
    class PlayerGetAttackStrength(val event: PlayerGetAttackStrengthEvent): SkillEvent()
    class CriticalHit(val event: CriticalHitEvent): SkillEvent()
    class SweepAttack(val event: SweepAttackEvent): SkillEvent()
    class Death(val event: LivingDeathEvent): SkillEvent()
    class TargetDeath(val event: LivingDeathEvent): SkillEvent()
    object Init: SkillEvent()
    object Start: SkillEvent()
    abstract class State(val state: SkillState): SkillEvent() {
        class Enter(state: SkillState): State(state)
        class Update(state: SkillState): State(state)
        class Exit(state: SkillState): State(state)
    }
    object End: SkillEvent()
    object PhysicsTick: SkillEvent()

    /**
     * 执行在技能参数初始化之后，函数初始化之前，因此可以正常拿取技能的持有对象和level参数，但不可涉及技能具体逻辑，因为此方法节点尚在技能初始化中，如有需要请调用[Init]
     */
    class ConfigInit(val config: SkillConfig): SkillEvent()
    class Sync(val data: CompoundTag, val context: IPayloadContext): SkillEvent()
}