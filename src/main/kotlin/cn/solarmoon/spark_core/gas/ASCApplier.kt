package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.local_control.KeyEvent
import cn.solarmoon.spark_core.local_control.onEvent
import cn.solarmoon.spark_core.state_machine.graph.ActionBehavior
import cn.solarmoon.spark_core.state_machine.graph.ActionController
import cn.solarmoon.spark_core.state_machine.graph.actionGraph
import cn.solarmoon.spark_core.state_machine.graph.go
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object ASCApplier {

    @SubscribeEvent
    fun setASC(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        entity.abilitySystemComponent = AbilitySystemComponent(entity, level)
    }

    @SubscribeEvent
    fun leave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.abilitySystemComponent.endAllAbilities()
    }

    val graph = actionGraph {
        initialNode("Idle") {
            on("Attack") go "spark_core:attack"
        }
        node("spark_core:attack") {
            on("Tick") go "Idle"
        }
    }

    val controller = lazy {
        ActionController(graph, object : ActionBehavior {
            override fun onEnter(controller: ActionController) {
                try {
                    val id = ResourceLocation.parse(controller.currentNode.id)
                    Minecraft.getInstance().player?.abilitySystemComponent?.apply {
                        SparkCore.LOGGER.error(allAbilitySpecs.toString())
                        tryActivateAbilityLocal(findSpecFromLocation(id)!!.handle, ActivationContext.Empty)
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.error(e.toString())
                }
            }
        })
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        AbilityTypeManager.allAbilityTypes.values
            .filter { it.tags.has(gameplayTag("sof")) }
            .forEach { entity.abilitySystemComponent.giveAbility(AbilitySpec(it)) }
    }

    @SubscribeEvent
    private fun playerInput(event: MovementInputUpdateEvent) {
        Minecraft.getInstance().options.keyAttack.onEvent(KeyEvent.PRESS_ONCE) {
            controller.value.pushInput("Attack")
            true
        }
        controller.value.triggerEvent("Tick")
    }

}