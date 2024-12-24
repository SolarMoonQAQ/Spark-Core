package cn.solarmoon.spark_core.entity.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries

interface Skill<T> {

    fun activate(ob: T)

    fun tick(ob: T)

    val registryKey get() = SparkRegistries.SKILL.getKey(this) ?: throw NullPointerException("Skill ${this.javaClass::getSimpleName} not yet registered.")

}