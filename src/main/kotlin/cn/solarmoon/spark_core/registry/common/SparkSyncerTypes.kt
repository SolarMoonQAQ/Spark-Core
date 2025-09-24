package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.sync.EntitySyncerType

object SparkSyncerTypes {

    @JvmStatic
    fun register() {}

    @JvmStatic
    val ENTITY = SparkCore.MC_REGISTER.syncerType()
        .id("entity")
        .bound { EntitySyncerType() }
        .build()

}