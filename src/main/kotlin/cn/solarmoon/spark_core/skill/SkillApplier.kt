package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.common.SyncerTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object SkillApplier {

    @SubscribeEvent
    private fun onEntityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.updateSkillGroup()
    }

    @SubscribeEvent
    private fun onEntityJoin(event: EntityJoinLevelEvent) {
        val level = event.level
        val entity = event.entity
        level.registryAccess().registryOrThrow(SparkRegistries.SKILL_GROUP).forEach {
            it.binders.forEach { (type, ids) ->
                if (type == SyncerTypes.ENTITY.get() && ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type))) {
                    entity.skillGroups[it.getRegistryKey(level.registryAccess())] = it
                }
            }
        }
    }

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.skillGroups.clear()
    }

}