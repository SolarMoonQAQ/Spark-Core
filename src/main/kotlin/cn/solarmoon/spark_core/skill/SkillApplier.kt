package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.EntityEvent
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
        entity.allSkills.values.forEach {
            it.update()
        }
    }

    @SubscribeEvent
    private fun onPhysicsTick(event: PhysicsEntityTickEvent) {
        event.entity.activeSkills.forEach { it.physicsTick() }
    }

    @SubscribeEvent
    private fun onHurt(event: LivingIncomingDamageEvent) {
        event.entity.activeSkills.forEach {
            it.hurt(event)
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.targetHurt(event)
        }
    }

    @SubscribeEvent
    private fun onActualHurt(event: LivingDamageEvent.Pre) {
        event.entity.activeSkills.forEach {
            it.damage(event)
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.targetDamage(event)
        }
    }

    @SubscribeEvent
    private fun onActualHurt(event: LivingDamageEvent.Post) {
        event.entity.activeSkills.forEach {
            it.damage(event)
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.targetDamage(event)
        }
    }

    @SubscribeEvent
    private fun playerInput(event: MovementInputUpdateEvent) {
        handle(event)
    }

    private fun handle(event: EntityEvent) {
        event.entity.activeSkills.forEach {
            it.handleEvent(event)
        }
    }

}