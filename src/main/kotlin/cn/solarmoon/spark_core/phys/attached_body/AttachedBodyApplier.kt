package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.phys.thread.getPhysWorld
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import org.ode4j.math.DVector3

class AttachedBodyApplier {

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
        val level = event.level
        entity.getData(SparkAttachments.BODY).values.forEach {
            level.getPhysWorld().laterConsume { it.body.destroy() }
        }
    }

}