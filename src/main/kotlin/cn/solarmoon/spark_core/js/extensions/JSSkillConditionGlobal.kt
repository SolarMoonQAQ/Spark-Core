package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.doc.JSGlobal
import cn.solarmoon.spark_core.skill.SkillCondition
import cn.solarmoon.spark_core.skill.SkillHost
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level

@JSGlobal("SkillCondition")
object JSSkillConditionGlobal {

    fun create(name: String, reason: String, condition: (SkillHost, Level) -> Boolean): SkillCondition {
        return SkillCondition(name, reason, condition)
    }

    fun isEntity(): SkillCondition = SkillCondition("isEntity", "技能持有者必须为实体") { holder, level -> holder is Entity }

    fun isAnimatable(): SkillCondition = SkillCondition("isAnimatable", "技能持有者必须为动画体") { holder, level -> holder is IAnimatable<*> }

}