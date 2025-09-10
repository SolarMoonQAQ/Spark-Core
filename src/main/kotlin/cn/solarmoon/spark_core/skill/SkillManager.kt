package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation
import java.util.Collections
import java.util.LinkedHashMap

object SkillManager: LinkedHashMap<ResourceLocation, SkillType<*>>() {

    private fun readResolve(): Any = SkillManager

    private val targetToSkills = hashMapOf<Any, MutableSet<Skill>>()

    fun registerSkillTarget(target: Any, skill: Skill) {
        targetToSkills.getOrPut(target) { Collections.synchronizedSet(mutableSetOf()) }.add(skill)
    }

    fun unregisterSkillTarget(target: Any, skill: Skill) {
        targetToSkills[target]?.remove(skill)
    }

    fun getSkillsByTarget(target: Any): Set<Skill> {
        return targetToSkills[target]?.toSet() ?: emptySet()
    }


    fun debugPrintSkillOrder(context: String) {
        SparkCore.LOGGER.debug("=== SkillManager 技能顺序验证: {} (线程: {}) ===", context, Thread.currentThread().name)
        this.entries.forEachIndexed { index, (key, skillType) ->
            SparkCore.LOGGER.debug("  [{}] {} -> {}", index, key, skillType.javaClass.simpleName)
        }
        SparkCore.LOGGER.debug("=== 总计: {} 个技能 ===", this.size)
    }

}