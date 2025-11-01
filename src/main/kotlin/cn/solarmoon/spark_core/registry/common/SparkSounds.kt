package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore

object SparkSounds {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val CUSTOM_SOUND = SparkCore.REGISTER.soundEvent {
        id = "custom_sound"
    }
}