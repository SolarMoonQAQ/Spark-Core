package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.StateMachineRegisterEvent
import cn.solarmoon.spark_core.state_machine.presets.EntityBaseUseAnimStateMachine
import cn.solarmoon.spark_core.state_machine.presets.PlayerBaseAnimStateMachine
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.common.NeoForge

object SparkStateMachineRegister {

    val PLAYER_BASE_STATE = id("player_base_state")
    val ENTITY_BASE_USE_STATE = id("entity_base_use_state")

    private fun reg(event: StateMachineRegisterEvent.Entity) {
        val entity = event.entity
        if (entity is Player) {
            event.register(PLAYER_BASE_STATE, PlayerBaseAnimStateMachine(entity))
        }
        if (entity is LivingEntity) {
            event.register(ENTITY_BASE_USE_STATE, EntityBaseUseAnimStateMachine(entity))
        }
    }

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, name)

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}