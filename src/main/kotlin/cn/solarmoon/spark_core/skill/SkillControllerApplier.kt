package cn.solarmoon.spark_core.skill

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object SkillControllerApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.getAllSkillControllers().forEach {
            it.baseTick()
            if (it.isAvailable()) it.tick()
        }
    }

    @SubscribeEvent
    private fun onHit(event: LivingIncomingDamageEvent) {
        val entity = event.entity
        entity.getAllSkillControllers().forEach {
            if (it.isAvailable()) it.onHurt(event)
        }
    }

}