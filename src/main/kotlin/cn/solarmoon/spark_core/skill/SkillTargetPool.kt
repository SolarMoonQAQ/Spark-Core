package cn.solarmoon.spark_core.skill

import net.minecraft.world.entity.Entity
import kotlin.collections.remove

class SkillTargetPool(private val skill: Skill) {

    private val targets = linkedSetOf<Any>()

    fun addTarget(entity: Any) {
        targets.add(entity)
        SkillManager.registerSkillTarget(entity, skill)
    }

    fun removeTarget(entity: Entity) {
        targets.remove(entity)
        SkillManager.unregisterSkillTarget(entity, skill)
    }

    fun getTargets() = targets.toList()

    fun clear() = targets.clear()

    fun forEach(consumer: (Any) -> Unit) = targets.forEach(consumer)

}