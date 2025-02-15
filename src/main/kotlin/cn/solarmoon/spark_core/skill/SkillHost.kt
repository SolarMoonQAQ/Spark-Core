package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.atomic.AtomicInteger

interface SkillHost: Syncer {

    val skillCount: AtomicInteger

    var activeSkillGroup: SkillGroup?

    val allSkills: MutableMap<Int, SkillInstance>

    val predictedSkills: MutableMap<Int, SkillInstance>

    val skillGroups: LinkedHashMap<ResourceLocation, SkillGroup>

    val isPlayingSkill get() = allSkills.any { it.value.isActive }

    fun updateSkillGroup() {
        val target = skillGroups.values.sortedByDescending { it.priority }.firstOrNull { it.checkConditions(this) }
        if (activeSkillGroup != target) {
            onSkillGroupExit(activeSkillGroup)
            onSkillGroupEntry(target)
            activeSkillGroup = target
        }
    }

    fun onSkillGroupEntry(group: SkillGroup?) {}

    fun onSkillGroupExit(group: SkillGroup?) {}

    fun getSkillGroup(id: ResourceLocation) = skillGroups[id]

}