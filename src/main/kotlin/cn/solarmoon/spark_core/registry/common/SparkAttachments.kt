package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.ModelData
import cn.solarmoon.spark_core.entity.attack.AttackedData
import cn.solarmoon.spark_core.skill.SkillController
import cn.solarmoon.spark_core.phys.attached_body.AttachedBody
import java.util.Optional


object SparkAttachments {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val ANIM_DATA = SparkCore.REGISTER.attachment<ModelData>()
        .id("anim_data")
        .defaultValue { ModelData.EMPTY }
        .serializer { it.serialize(ModelData.CODEC) }
        .build()

    @JvmStatic
    val ATTACKED_DATA = SparkCore.REGISTER.attachment<Optional<AttackedData>>()
        .id("attacked_data")
        .defaultValue { Optional.empty() }
        .build()

    @JvmStatic
    val BODY = SparkCore.REGISTER.attachment<LinkedHashMap<String, AttachedBody>>()
        .id("attached_body")
        .defaultValue { linkedMapOf() }
        .build()

    @JvmStatic
    val SKILL_CONTROLLER = SparkCore.REGISTER.attachment<MutableList<SkillController<*>>>()
        .id("skill_controller")
        .defaultValue { mutableListOf() }
        .build()

}