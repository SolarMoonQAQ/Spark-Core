package cn.solarmoon.spark_core.js

import cn.solarmoon.spark_core.util.PPhase
import net.minecraft.client.Minecraft
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent

object SparkJsApplier {

    @SubscribeEvent
    private fun onLevelLoad(event: ServerStartingEvent) {
        val js = ServerSparkJS()
        SparkJS.ALL[false] = js
        js.register()
        js.loadAll()
    }

    var i = false

    @SubscribeEvent
    private fun onClientLoad(event: ClientTickEvent.Pre) {
        if (!i) {
            i = true
            val js = ClientSparkJS()
            SparkJS.ALL[true] = js
            js.register()
        }
    }

}