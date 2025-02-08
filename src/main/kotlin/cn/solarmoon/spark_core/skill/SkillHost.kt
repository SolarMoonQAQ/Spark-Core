package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.resources.ResourceLocation

interface SkillHost: Syncer {

    var activeSkillGroup: SkillGroup?

    val activeSkills: MutableSet<SkillInstance>

    val skillGroups: LinkedHashMap<ResourceLocation, SkillGroup>

    val isPlayingSkill get() = activeSkills.isNotEmpty()

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