package cn.solarmoon.spark_core.resource2

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent

object SparkPackLoaderApplier {

    @SubscribeEvent
    fun serverStart(event: ServerAboutToStartEvent) {
        SparkPackLoader.apply {
            initialize(false)
            readPackageGraph()
            readPackageContent(false)
        }
    }

    fun clientStart(event: FMLClientSetupEvent) {
        event.enqueueWork {
            SparkPackLoader.initialize(true)
        }
    }

}