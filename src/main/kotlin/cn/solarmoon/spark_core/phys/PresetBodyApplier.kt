package cn.solarmoon.spark_core.phys

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.OnBodyCreateEvent
import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.registry.common.SparkBodyTypes
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent

object PresetBodyApplier {

    @SubscribeEvent
    private fun onBodyCreate(event: OnBodyCreateEvent) {
        val body = event.body
        val owner = body.owner
        if (owner is Entity) owner.getData(SparkAttachments.BODIES).add(body)
    }

    @SubscribeEvent
    private fun onJoinLevel(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        createEntityBoundingBoxBody(SparkBodyTypes.ENTITY_BOUNDING_BOX.get(), entity, level)
    }

    @SubscribeEvent
    private fun onLeaveLevel(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        val level = event.level
        level.getPhysLevel().world.laterConsume {
            entity.getData(SparkAttachments.BODIES).forEach { it.destroy() }
        }
    }

}