package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.event.PhysicsTickEvent
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer.Companion.ALL_VISUAL_EFFECTS
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

object VisualEffectTicker {

    @SubscribeEvent
    private fun tick(event: ClientTickEvent.Pre) {
        ALL_VISUAL_EFFECTS.forEach { it.tick() }
    }

    @SubscribeEvent
    private fun physTick(event: PhysicsTickEvent.Level) {
        ALL_VISUAL_EFFECTS.forEach { it.physTick(event.level) }
    }

}