package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.gas.ActivationContext
import cn.solarmoon.spark_core.state_machine.graph.StateAction
import cn.solarmoon.spark_core.state_machine.graph.StateCondition
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

object SparkRegistries {

    // gas
    val ABILITY_TYPE_CODEC = SparkCore.REGISTER.registry<MapCodec<out AbilityType.Serializer>>("ability_type_codec") {
        it.sync(true).create()
    }

    val ACTIVATION_CONTEXT_CODEC = SparkCore.REGISTER.registry<MapCodec<out ActivationContext>>("activation_context_codec") {
        it.sync(true).create()
    }

    val ACTIVATION_CONTEXT_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out ActivationContext>>("activation_context_stream_codec") {
        it.sync(true).create()
    }

    // state machine
    val STATE_CONDITION_CODEC = SparkCore.REGISTER.registry<MapCodec<out StateCondition>>("state_condition") {
        it.sync(true).create()
    }

    val STATE_ACTION_CODEC = SparkCore.REGISTER.registry<MapCodec<out StateAction>>("state_action") {
        it.sync(true).create()
    }

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType<*>>("syncer_type") {
        it.sync(true).create()
    }

    @JvmStatic
    val SYNC_DATA_CODEC = SparkCore.REGISTER.registry<MapCodec<out SyncData>>("sync_data_codec") {
        it.sync(true).create()
    }

    @JvmStatic
    val SYNC_DATA_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out SyncData>>("sync_data_stream_codec") {
        it.sync(true).create()
    }

    @JvmStatic
    val DIFF_SYNC_SCHEMA = SparkCore.REGISTER.registry<DiffSyncSchema<*>>("diff_schema") {
        it.sync(true).create()
    }

    @JvmStatic
    fun register() {}
}
