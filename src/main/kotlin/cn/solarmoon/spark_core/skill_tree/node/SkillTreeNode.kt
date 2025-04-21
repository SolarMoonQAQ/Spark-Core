package cn.solarmoon.spark_core.skill_tree.node

import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill_tree.SkillTree
import net.minecraft.world.level.Level

class SkillTreeNode(
    val skillType: SkillType<*>,
    val preInputId: String,
    val preInputDuration: Int,
    val reserveTime: Int = 0,
    val children: List<SkillTreeNode> = listOf(),
    val condition: (SkillHost, Skill?) -> Boolean = { _, _ -> true }
) {

    fun nextNode(index: Int) = children.getOrNull(index)

    fun match(host: SkillHost, skill: Skill?) = condition(host, skill)

    fun onEntry(host: SkillHost, level: Level, tree: SkillTree): Boolean {
        skillType.createSkill(host, level, true)
        return true
    }

}