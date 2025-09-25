package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.math.Vector3f

object SparkCollisionShapeTypes {
    @JvmStatic
    fun register() {}

    val DEFAULT_BOX = SparkCore.REGISTER.collisionShapeType<BoxCollisionShape>()
        .id("default_box")
        .bound { BoxCollisionShape(Vector3f()) }
        .build()

    val TEST_BOX = SparkCore.REGISTER.collisionShapeType<BoxCollisionShape>()
        .id("test_box")
        .bound { BoxCollisionShape(Vector3f(0.5f, 0.5f, 0.5f)) }
        .build()

}