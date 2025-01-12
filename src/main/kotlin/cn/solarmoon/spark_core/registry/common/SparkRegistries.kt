package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.state_control.ObjectState

object SparkRegistries {

    @JvmStatic
    val SKILL_TYPE = SparkCore.REGISTER.registry<SkillType<*, *>>()
        .id("skill_type")
        .build { it.sync(true).create() }

    @JvmStatic
    val STATE = SparkCore.REGISTER.registry<ObjectState<*>>()
        .id("state")
        .build { it.sync(true).create() }

    @JvmStatic
    fun register() {}

}