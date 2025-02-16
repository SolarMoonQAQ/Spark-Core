package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.ItemAnimatable

object SparkDataComponents {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val ANIMATABLE = SparkCore.REGISTER.dataComponent<ItemAnimatable>()
        .id("animatable")
        .build {
            it.persistent(ItemAnimatable.CODEC)
                .networkSynchronized(ItemAnimatable.STREAM_CODEC)
                .cacheEncoding()
                .build()
        }

}