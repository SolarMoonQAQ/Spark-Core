package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore

object SparkBodyTypes {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val CUSTOM = SparkCore.REGISTER.bodyType()
        .id("custom")
        .build()

    @JvmStatic
    val ENTITY_BOUNDING_BOX = SparkCore.REGISTER.bodyType()
        .id("entity_bounding_box")
        .build()

}