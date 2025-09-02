package cn.solarmoon.spark_core.resource2

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent

object SparkPackLoaderApplier {

    @SubscribeEvent
    fun serverStart(event: ServerAboutToStartEvent) {
        SparkPackLoader.readPackageGraph()
        SparkPackLoader.readPackageContent()
    }

}