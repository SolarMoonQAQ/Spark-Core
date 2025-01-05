package cn.solarmoon.spark_core.animation.anim.auto_anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AutoAnimApplier {

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.autoAnims.forEach {
                if (it is EntityAutoAnim) it.tick()
            }
        }
    }

    @SubscribeEvent
    private fun entityHit(event: LivingDamageEvent.Post) {
        val entity = event.entity
        if (event.newDamage <= 0) return
        if (entity is IEntityAnimatable<*>) {
            entity.autoAnims.forEach {
                if (it is HitAutoAnim) it.onActualHit(event)
            }
        }
    }

}