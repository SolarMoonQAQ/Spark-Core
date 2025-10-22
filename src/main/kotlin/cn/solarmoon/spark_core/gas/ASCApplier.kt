package cn.solarmoon.spark_core.gas

import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent

object ASCApplier {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
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

}