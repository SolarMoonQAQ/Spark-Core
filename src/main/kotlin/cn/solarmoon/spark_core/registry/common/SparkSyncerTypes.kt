package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.core.BlockPos

object SparkSyncerTypes {

    @JvmStatic
    fun register() {}

    @JvmStatic
    val ENTITY = SparkCore.REGISTER.syncerType {
        id = "entity"
        factory = {
            SyncerType { level, data -> level.getEntity(data.data as Int) }
        }
    }

    val BLOCK_ENTITY = SparkCore.REGISTER.syncerType {
        id = "block_entity"
        factory = {
            SyncerType { level, data -> level.getBlockEntity(data.data as BlockPos) }
        }
    }

}