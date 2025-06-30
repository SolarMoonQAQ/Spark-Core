package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.ik.visualizer.IKDebugRenderer
import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShaker
import cn.solarmoon.spark_core.visual_effect.shadow.ShadowRenderer
import cn.solarmoon.spark_core.visual_effect.shape.ShapeRenderer
import cn.solarmoon.spark_core.visual_effect.trail.TrailRenderer

object SparkVisualEffects {

    @JvmStatic
    val TRAIL = TrailRenderer()

    @JvmStatic
    val SHADOW = ShadowRenderer()

    @JvmStatic
    val CAMERA_SHAKE = CameraShaker()

    @JvmStatic
    val SPORT = ShapeRenderer()

    @JvmStatic
    val IK = IKDebugRenderer()

    @JvmStatic
    fun register() {}

}