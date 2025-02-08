package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

fun getSkillType(level: Level, key: ResourceLocation) =
    level.registryAccess().registryOrThrow(SparkRegistries.SKILL_TYPE).get(key) ?: throw NullPointerException("技能 $key 不存在或尚未注册")