package cn.solarmoon.spark_core.js

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

object SparkJSApplier {

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Pre) {
        SparkJS.get(false).tickPre()
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Pre) {
        SparkJS.get(true).tickPre()
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        SparkJS.get(false).tickPost()
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        SparkJS.get(true).tickPost()
    }

}