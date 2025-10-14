package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.AbilitySpec
import cn.solarmoon.spark_core.gas.ActivationContext
import cn.solarmoon.spark_core.gas.AttackAbilityTypeSerializer
import cn.solarmoon.spark_core.skill.graph.ActionCondition
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

        event.register(SparkRegistries.ACTION_CONDITION_CODEC.key(), id("true")) { ActionCondition.True.codec }
        event.register(SparkRegistries.ACTION_CONDITION_CODEC.key(), id("false")) { ActionCondition.False.codec }
        event.register(SparkRegistries.ACTION_CONDITION_CODEC.key(), id("reverse")) { ActionCondition.Reverse.CODEC }
        event.register(SparkRegistries.ACTION_CONDITION_CODEC.key(), id("all")) { ActionCondition.All.CODEC }
        event.register(SparkRegistries.ACTION_CONDITION_CODEC.key(), id("any")) { ActionCondition.Any.CODEC }

        event.register(SparkRegistries.ABILITY_SPEC_STREAM_CODEC.key(), id("common")) { AbilitySpec.streamCodec }
        event.register(SparkRegistries.ACTIVATION_CONTEXT_STREAM_CODEC.key(), id("empty")) { ActivationContext.Empty.streamCodec }

        event.register(SparkRegistries.ABILITY_TYPE_CODEC.key(), id("attack")) { AttackAbilityTypeSerializer.CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }

}