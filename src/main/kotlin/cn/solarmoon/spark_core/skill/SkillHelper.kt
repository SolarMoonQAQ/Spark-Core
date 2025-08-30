package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.resources.ResourceLocation

fun skillType(
    id: ResourceLocation,
    conditions: List<SkillStartCondition> = listOf(),
    builder: Skill.() -> Unit
): SkillType<Skill> = skillType(id, conditions, { Skill() }, builder)

inline fun <reified T: Skill> skillType(
    id: ResourceLocation,
    conditions: List<SkillStartCondition> = listOf(),
    crossinline origin: () -> T,
    crossinline builder: T.() -> Unit
): SkillType<T> {
    val type = SkillType(id, conditions = conditions) {
        val skill = origin()
        builder.invoke(skill)
        skill
    }
    // 添加调试日志来验证注册顺序
    SparkCore.LOGGER.debug("注册技能类型: {} (当前总数: {}, 线程: {})", 
        id, SkillManager.size + 1, Thread.currentThread().name)
    SkillManager[id] = type
    return type
}