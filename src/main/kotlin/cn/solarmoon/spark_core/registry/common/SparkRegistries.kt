package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.phys.BodyType

object SparkRegistries {

    @JvmStatic
    val TYPED_ANIMATION = SparkCore.REGISTER.registry<TypedAnimation>()
        .id("typed_animation")
        .build { it.sync(true).create() }

    @JvmStatic
    val BODY_TYPE = SparkCore.REGISTER.registry<BodyType>()
        .id("body_type")
        .build { it.sync(true).create() }

    @JvmStatic
    fun register() {}

}