package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.entity.state.EntityAnimStateMachine
import cn.solarmoon.spark_core.event.StateMachineRegisterEvent
import cn.solarmoon.spark_core.state_control.StateMachine
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.common.NeoForge

object SparkStateMachineRegister {

    private fun reg(event: StateMachineRegisterEvent.Entity) {
        fun add(id: String, stateMachine: (Entity) -> StateMachine<Entity>) {
            event.register(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id), stateMachine.invoke(event.holder))
        }

        add("common", ::EntityAnimStateMachine)
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }

}