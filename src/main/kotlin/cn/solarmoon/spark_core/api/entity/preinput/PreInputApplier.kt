package cn.solarmoon.spark_core.api.entity.preinput

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

class PreInputApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.getPreInput().tick()
    }

}