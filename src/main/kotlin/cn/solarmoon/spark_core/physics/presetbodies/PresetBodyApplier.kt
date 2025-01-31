package cn.solarmoon.spark_core.physics.presetbodies

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent

object PresetBodyApplier {

    @SubscribeEvent
    private fun onJoinLevel(event: EntityJoinLevelEvent) {
        val level = event.level
        val entity = event.entity


    }

}