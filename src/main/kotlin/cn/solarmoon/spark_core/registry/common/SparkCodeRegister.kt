package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.ActivationContext
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.state_machine.graph.conditions.HasTagCondition
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

        event.register(SparkRegistries.STATE_CONDITION_CODEC.key(), id("true")) { StateCondition.True.codec }
        event.register(SparkRegistries.STATE_CONDITION_CODEC.key(), id("false")) { StateCondition.False.codec }
        event.register(SparkRegistries.STATE_CONDITION_CODEC.key(), id("reverse")) { StateCondition.Reverse.CODEC }
        event.register(SparkRegistries.STATE_CONDITION_CODEC.key(), id("all")) { StateCondition.All.CODEC }
        event.register(SparkRegistries.STATE_CONDITION_CODEC.key(), id("any")) { StateCondition.Any.CODEC }
        event.register(SparkRegistries.STATE_CONDITION_CODEC.key(), id("has_tag")) { HasTagCondition.CODEC }

        event.register(SparkRegistries.ACTIVATION_CONTEXT_CODEC.key(), id("empty")) { ActivationContext.Empty.codec }
        event.register(SparkRegistries.ACTIVATION_CONTEXT_STREAM_CODEC.key(), id("empty")) { ActivationContext.Empty.streamCodec }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}