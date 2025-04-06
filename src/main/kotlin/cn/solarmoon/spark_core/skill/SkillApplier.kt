package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.event.MolangQueryRegisterEvent
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object SkillApplier {

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.allSkills.values.forEach { it.end() }
    }

    @SubscribeEvent
    private fun onEntityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.activeSkills.forEach {
            it.update()
        }
    }

    @SubscribeEvent
    private fun onPhysicsTick(event: PhysicsEntityTickEvent) {
        event.entity.activeSkills.forEach { it.triggerEvent(SkillEvent.PhysicsTick) }
    }

    @SubscribeEvent
    private fun onHurt(event: LivingIncomingDamageEvent) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.Hurt(event))
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.triggerEvent(SkillEvent.TargetHurt(event))
        }
    }

    @SubscribeEvent
    private fun onActualHurt(event: LivingDamageEvent.Pre) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.ActualHurt(event))
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.triggerEvent(SkillEvent.TargetActualHurt(event))
        }
    }

    @SubscribeEvent
    private fun onActualHurt(event: LivingDamageEvent.Post) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.ActualHurt(event))
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.triggerEvent(SkillEvent.TargetActualHurt(event))
        }
    }

    @SubscribeEvent
    private fun onKnockBack(event: LivingKnockBackEvent) {
        val entity = event.entity
        entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.KnockBack(event))
        }

        SkillManager.getSkillsByTarget(entity).forEach {
            it.triggerEvent(SkillEvent.TargetKnockBack(event))
        }
    }

    @SubscribeEvent
    private fun playerInput(event: MovementInputUpdateEvent) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.LocalInputUpdate(event))
        }
    }

}