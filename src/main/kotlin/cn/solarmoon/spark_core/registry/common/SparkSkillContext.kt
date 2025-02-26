package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.skill.Key
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity

object SparkSkillContext {

    val ENTITY_TARGET = Key.create<Entity>("entity_target")
    val TIME = Key.create<Double>("time")
    val DAMAGE_SOURCE = Key.create<DamageSource>("damage_source")

}