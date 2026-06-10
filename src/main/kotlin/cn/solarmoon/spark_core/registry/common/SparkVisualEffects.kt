package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.ik.visualizer.IKDebugRenderer
import cn.solarmoon.spark_core.particle.client.render.ParticleVisualEffectRenderer
import cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShaker
import cn.solarmoon.spark_core.visual_effect.shape.ShapeRenderer
import cn.solarmoon.spark_core.visual_effect.trail.TrailRenderer

object SparkVisualEffects {

    @JvmStatic
    val TRAIL = TrailRenderer()

    @JvmStatic
    val CAMERA_SHAKE = CameraShaker()

    @JvmStatic
    val SPORT = ShapeRenderer()

    @JvmStatic
    val IK = IKDebugRenderer()

    @JvmStatic
    val PARTICLE = ParticleVisualEffectRenderer()

    @JvmStatic
    fun register() {}

}