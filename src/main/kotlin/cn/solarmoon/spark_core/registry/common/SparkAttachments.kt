package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex

object SparkAttachments {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val MODEL_INDEX = SparkCore.REGISTER.attachment<ModelIndex>()
        .id("model_index")
        .defaultValue { ModelIndex.EMPTY }
        .serializer { it.serialize(ModelIndex.CODEC) }
        .build()

}