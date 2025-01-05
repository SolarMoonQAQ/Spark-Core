package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import kotlinx.coroutines.launch
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent

object AttachedBodyApplier {

    @SubscribeEvent
    private fun join(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = event.level
        val body = EntityBoundingBoxBody(level, entity)
        entity.putBody(body)
    }

    @SubscribeEvent
    private fun leave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.getData(SparkAttachments.BODY).values.forEach {
            it.physLevel.physWorld.laterConsume { it.body.destroy() }
        }
    }

}