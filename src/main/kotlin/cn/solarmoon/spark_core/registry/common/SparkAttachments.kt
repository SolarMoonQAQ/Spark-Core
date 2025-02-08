package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.flag.Flag


object SparkAttachments {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val MODEL_INDEX = SparkCore.REGISTER.attachment<ModelIndex>()
        .id("model_index")
        .defaultValue { ModelIndex.EMPTY }
        .serializer { it.serialize(ModelIndex.CODEC) }
        .build()

    @JvmStatic
    val FLAG = SparkCore.REGISTER.attachment<LinkedHashMap<Flag, Boolean>>()
        .id("flag")
        .defaultValue { linkedMapOf() }
        .serializer { it.serialize(Flag.MAP_CODEC) }
        .build()

}