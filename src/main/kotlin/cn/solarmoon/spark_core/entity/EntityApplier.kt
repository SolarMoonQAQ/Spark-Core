package cn.solarmoon.spark_core.entity

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent

object EntityApplier {

    @SubscribeEvent
    private fun onKnockBack(event: LivingKnockBackEvent) {
        val entity = event.entity
        if (!entity.canKnockBack) event.isCanceled = true
    }

}