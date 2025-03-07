package cn.solarmoon.spark_core.registry.client

import cn.solarmoon.spark_core.SparkCore
import org.lwjgl.glfw.GLFW

object SparkKeyMappings {

    @JvmStatic
    val OPEN_ANIMATION_DEBUG = SparkCore.REGISTER.keyMapping()
        .id("key.spark_core.open_animation_debug")
        .bound(GLFW.GLFW_KEY_F8)
        .build()

    @JvmStatic
    fun register() {}

}