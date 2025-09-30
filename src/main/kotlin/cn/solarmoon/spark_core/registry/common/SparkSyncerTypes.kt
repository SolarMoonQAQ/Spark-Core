package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity

object SparkSyncerTypes {

    @JvmStatic
    fun register() {}

    @JvmStatic
    val ENTITY = SparkCore.REGISTER.syncerType<Entity>()
        .id("entity")
        .bound { SyncerType { level, data -> level.getEntity(data.data as Int) } }
        .build()

    val BLOCK_ENTITY = SparkCore.REGISTER.syncerType<BlockEntity>()
        .id("block_entity")
        .bound { SyncerType { level, data -> level.getBlockEntity(data.data as BlockPos) } }
        .build()

}