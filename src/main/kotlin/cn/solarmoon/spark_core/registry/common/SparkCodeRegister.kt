package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.sync.BlockPosSyncData
import cn.solarmoon.spark_core.sync.IntSyncData
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object SparkCodeRegister {

    private fun reg(event: RegisterEvent) {
        event.register(SparkRegistries.SYNC_DATA_STREAM_CODEC.key(), id("int")) { IntSyncData.STREAM_CODEC }
        event.register(SparkRegistries.SYNC_DATA_CODEC.key(), id("int")) { IntSyncData.CODEC }
        event.register(SparkRegistries.SYNC_DATA_STREAM_CODEC.key(), id("block_pos")) { BlockPosSyncData.STREAM_CODEC }
        event.register(SparkRegistries.SYNC_DATA_CODEC.key(), id("block_pos")) { BlockPosSyncData.CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}