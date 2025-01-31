package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShaker
import cn.solarmoon.spark_core.visual_effect.shadow.ShadowRenderer
import cn.solarmoon.spark_core.visual_effect.sport.SportRenderer
import cn.solarmoon.spark_core.visual_effect.trail.TrailRenderer

object SparkVisualEffects {

    @JvmStatic
    val TRAIL = TrailRenderer()

    @JvmStatic
    val SHADOW = ShadowRenderer()

    @JvmStatic
    val CAMERA_SHAKE = CameraShaker()

    @JvmStatic
    val SPORT = SportRenderer()

    @JvmStatic
    fun register() {}

}