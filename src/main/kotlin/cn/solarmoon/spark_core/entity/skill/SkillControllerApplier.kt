package cn.solarmoon.spark_core.entity.skill

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

class SkillControllerApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.getAllSkillControllers().forEach {
            it.baseTick()
            if (it.isAvailable()) it.tick()
        }
    }

}