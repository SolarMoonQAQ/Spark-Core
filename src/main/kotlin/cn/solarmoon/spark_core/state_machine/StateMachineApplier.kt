package cn.solarmoon.spark_core.state_machine

import cn.solarmoon.spark_core.event.StateMachineRegisterEvent
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object StateMachineApplier {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private fun entityTick(event: EntityTickEvent.Post) {
        val entity = event.entity
        entity.stateMachineHandlers.values.forEach {
            if (it.isActive) it.progress()
        }
    }

    @SubscribeEvent
    private fun entityJoin(event: EntityJoinLevelEvent) {
        NeoForge.EVENT_BUS.post(StateMachineRegisterEvent.Entity(event.entity.stateMachineHandlers, event.entity))
    }

}