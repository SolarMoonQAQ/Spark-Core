package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.gas.AbilityEvent
import cn.solarmoon.spark_core.gas.AbilitySpecType
import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.skill.graph.ActionCondition
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

object SparkRegistries {

    val ABILITY_TYPE = SparkCore.REGISTER.registry<AbilityType<*>>("ability_type") {
        it.sync(true).create()
    }

    val ABILITY_SPEC_TYPE = SparkCore.REGISTER.registry<AbilitySpecType<*, *>>("ability_spec_type") {
        it.sync(true).create()
    }

    val ACTION_CONDITION_CODEC = SparkCore.REGISTER.registry<MapCodec<out ActionCondition>>("action_condition") {
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
