package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.component.RigidBodyComponent

object SparkCollisionObjectTypes {
    @JvmStatic
    fun register() {}

    val RIGID_BODY = SparkCore.REGISTER.collisionObjectType<RigidBodyComponent>()
        .id("rigid_body")
        .bound(::RigidBodyComponent)
        .build()

}