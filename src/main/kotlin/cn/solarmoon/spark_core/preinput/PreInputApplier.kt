package cn.solarmoon.spark_core.preinput

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object PreInputApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Post) {
        val entity = event.entity
        val preInput = entity.preInput
        preInput.tick()

        preInput.tryExecute()
    }

}