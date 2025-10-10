package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.entity.moveCheck
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import cn.solarmoon.spark_core.event.PlayerGetAttackStrengthEvent
import cn.solarmoon.spark_core.local_control.KeyEvent
import cn.solarmoon.spark_core.local_control.onEvent
import cn.solarmoon.spark_core.skill.graph.ActionCondition
import cn.solarmoon.spark_core.skill.graph.ActionController
import cn.solarmoon.spark_core.skill.graph.ActionExitCondition
import cn.solarmoon.spark_core.skill.graph.ActionGraph
import cn.solarmoon.spark_core.skill.graph.ActionNode
import cn.solarmoon.spark_core.skill.graph.actionGraph
import cn.solarmoon.spark_core.util.triggerEvent
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import java.util.UUID

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
            it.triggerEvent(SkillEvent.ActualHurt.Pre(event))
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.triggerEvent(SkillEvent.TargetActualHurt.Pre(event))
        }
    }

    @SubscribeEvent
    private fun onActualHurt(event: LivingDamageEvent.Post) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.ActualHurt.Post(event))
        }

        SkillManager.getSkillsByTarget(event.entity).forEach {
            it.triggerEvent(SkillEvent.TargetActualHurt.Post(event))
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

    val graph = actionGraph {
        initialNode("Idle") {
            onInput("Attack", "Combo1", object : ActionCondition {
                override fun check(controller: ActionController): Boolean {
                    return controller.host is Player && (controller.host as Player).deltaMovement.horizontalDistance() > 0.05f
                }
            })
        }
        node("Combo1") {
            onInput("Attack", "Combo2")
        }
        node("Combo2") {
            onInput("Attack", "Combo3")
        }
        node("Combo3", ActionExitCondition.True) {

        }
    }

    private val controllers = mutableMapOf<UUID, ActionController>()

    @SubscribeEvent
    private fun playerInput(event: MovementInputUpdateEvent) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.LocalInputUpdate(event))
        }

        controllers.computeIfAbsent(event.entity.uuid) { ActionController(event.entity, graph) }

        Minecraft.getInstance().options.keyAttack.onEvent(KeyEvent.PRESS_ONCE) {
            controllers[event.entity.uuid]!!.onInput("Attack")
            true
        }

        controllers[event.entity.uuid]!!.tick()
    }

    @SubscribeEvent
    private fun playerAttackStrength(event: PlayerGetAttackStrengthEvent) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.PlayerGetAttackStrength(event))
        }
    }

    @SubscribeEvent
    private fun critical(event: CriticalHitEvent) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.CriticalHit(event))
        }
    }

    @SubscribeEvent
    private fun sweep(event: SweepAttackEvent) {
        event.entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.SweepAttack(event))
        }
    }

    @SubscribeEvent
    private fun die(event: LivingDeathEvent) {
        val entity = event.entity
        entity.activeSkills.forEach {
            it.triggerEvent(SkillEvent.Death(event))
        }

        SkillManager.getSkillsByTarget(entity).forEach {
            it.triggerEvent(SkillEvent.TargetDeath(event))
        }
    }

}