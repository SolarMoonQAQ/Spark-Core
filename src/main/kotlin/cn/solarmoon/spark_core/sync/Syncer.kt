package cn.solarmoon.spark_core.sync

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.network.codec.StreamCodec

interface Syncer {

    val syncerType: SyncerType

    val syncData: SyncData

}