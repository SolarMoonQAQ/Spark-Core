package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.visual_effect.common.camera_shake.CameraShaker
import cn.solarmoon.spark_core.visual_effect.common.shadow.ShadowRenderer
import cn.solarmoon.spark_core.visual_effect.common.trail.TrailRenderer

object SparkVisualEffects {

    @JvmStatic
    val TRAIL = TrailRenderer()

    @JvmStatic
    val SHADOW = ShadowRenderer()

    @JvmStatic
    val CAMERA_SHAKE = CameraShaker()

    @JvmStatic
    fun register() {}

}