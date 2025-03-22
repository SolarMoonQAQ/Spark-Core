package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.model.origin.OModel
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.data.DataProvider
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider
import net.neoforged.neoforge.data.event.GatherDataEvent

object SparkDataGenerator {

    @SubscribeEvent
    private fun gather(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        val lookupProvider = event.lookupProvider
        val helper = event.existingFileHelper

    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::gather)
    }

}