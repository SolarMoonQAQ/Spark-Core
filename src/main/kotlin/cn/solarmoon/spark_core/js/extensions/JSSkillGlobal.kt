package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.js.doc.JSGlobal
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillManager
import cn.solarmoon.spark_core.skill.SkillCondition
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.skillType
import net.minecraft.resources.ResourceLocation

@JSGlobal("Skill")
object JSSkillGlobal {

    /**
     * 创建技能
     * @param id 技能id
     * @return 技能类型
     */
    fun create(id: String, conditions: List<SkillCondition>, provider: (Skill) -> Unit): SkillType<*> =
        skillType(ResourceLocation.parse(id), conditions, provider).apply { fromScript = true }

    fun createBy(id: String, extend: String, conditions: List<SkillCondition>, provider: (Skill) -> Unit): SkillType<*> {
        val type = SkillManager.get(ResourceLocation.parse(extend)) ?: throw NullPointerException("父技能 $extend 无法被继承，因为此技能尚未注册")
        return skillType(ResourceLocation.parse(id), conditions.toMutableList().apply { addAll(type.conditions) }, { type.provider() }, provider).apply {
            fromScript = true
        }
    }

}

