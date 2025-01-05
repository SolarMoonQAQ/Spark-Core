package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AnimTicker {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        val level = entity.level()
        if (entity is IEntityAnimatable<*>) {
            // 基本tick
            entity.animController.tick()
        }
    }

}