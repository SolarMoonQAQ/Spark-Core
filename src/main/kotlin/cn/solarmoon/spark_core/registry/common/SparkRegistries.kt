package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.delta_sync.DiffSyncSchema
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

object SparkRegistries {

    @JvmStatic
    val TYPED_ANIMATION = SparkCore.REGISTER.registry<TypedAnimation>()
        .id("typed_animation")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType<*>>()
        .id("syncer_type")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNC_DATA_CODEC = SparkCore.REGISTER.registry<MapCodec<out SyncData>>()
        .id("sync_data_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNC_DATA_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out SyncData>>()
        .id("sync_data_stream_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val DIFF_SYNC_SCHEMA = SparkCore.REGISTER.registry<DiffSyncSchema<*>>()
        .id("diff_schema")
        .build { it.sync(true).create() }

    @JvmStatic
    fun register() {}

}
