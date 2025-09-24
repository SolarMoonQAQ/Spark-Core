package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.component.RigidBodyComponent

object SparkDiffSyncSchemas {
    @JvmStatic
    fun register() {}

    val RIGID_BODY_DATA = SparkCore.REGISTER.diffSyncSchema<RigidBodyComponent>()
        .id("rigid_body_data")
        .customBound()
        .build()

}