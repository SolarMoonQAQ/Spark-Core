package cn.solarmoon.spark_core.skill

import net.minecraft.world.entity.Entity
import java.util.Collections

object SkillManager {

    private val targetToSkills = hashMapOf<Entity, MutableSet<Skill>>()

    fun registerSkillTarget(target: Entity, skill: Skill) {
        targetToSkills.getOrPut(target) { Collections.synchronizedSet(mutableSetOf()) }.add(skill)
    }

    fun unregisterSkillTarget(target: Entity, skill: Skill) {
        targetToSkills[target]?.remove(skill)
    }

    fun getSkillsByTarget(target: Entity): Set<Skill> {
        return targetToSkills[target]?.toSet() ?: emptySet()
    }

}