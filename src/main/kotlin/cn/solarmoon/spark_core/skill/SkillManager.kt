package cn.solarmoon.spark_core.skill

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
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

}