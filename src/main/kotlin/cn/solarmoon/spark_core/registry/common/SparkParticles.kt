package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.particle.AnimatableShadowParticle

object SparkParticles {
    @JvmStatic
    fun register() {}

    val ANIMATABLE_SHADOW = SparkCore.REGISTER.particle<AnimatableShadowParticle.Option>()
        .id("animatable_shadow")
        .bound(true, AnimatableShadowParticle.Option::codec, AnimatableShadowParticle.Option::streamCodec)
        .build()

}