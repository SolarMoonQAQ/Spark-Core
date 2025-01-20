package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.entity.attack.AttackedData
import cn.solarmoon.spark_core.flag.Flag
import cn.solarmoon.spark_core.phys.BodyType
import org.ode4j.ode.DBody
import java.util.Optional


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
    val BODIES = SparkCore.REGISTER.attachment<MutableList<DBody>>()
        .id("body")
        .defaultValue { mutableListOf() }
        .build()

    @JvmStatic
    val FLAG = SparkCore.REGISTER.attachment<LinkedHashMap<Flag, Boolean>>()
        .id("flag")
        .defaultValue { linkedMapOf() }
        .serializer { it.serialize(Flag.MAP_CODEC) }
        .build()

}