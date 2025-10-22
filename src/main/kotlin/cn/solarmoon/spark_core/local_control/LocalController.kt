package cn.solarmoon.spark_core.local_control

import net.minecraft.client.KeyMapping
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.common.NeoForge

object LocalController {

    internal val keyRecorder = hashMapOf<KeyMapping, Boolean>()
    internal val keyPressTimer = hashMapOf<KeyMapping, Int>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    private fun tick(event: MovementInputUpdateEvent) {
        KeyMapping.ALL.values.forEach { key ->
            if (key.isDown) {
                keyPressTimer[key] = keyPressTimer.getOrPut(key) { 0 } + 1
            } else keyPressTimer[key] = 0

            keyRecorder[key] = key.isDown
        }
    }

}