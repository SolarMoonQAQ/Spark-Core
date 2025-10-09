package cn.solarmoon.spark_core.skill

import net.minecraft.resources.ResourceLocation

fun skillType(
    id: ResourceLocation,
    conditions: List<SkillCondition> = listOf(),
    builder: Skill.() -> Unit
): SkillType<Skill> = skillType(id, conditions, { Skill() }, builder)

inline fun <reified T: Skill> skillType(
    id: ResourceLocation,
    conditions: List<SkillCondition> = listOf(),
    crossinline origin: () -> T,
    crossinline builder: T.() -> Unit
): SkillType<T> {
    val type = SkillType(id, conditions = conditions) {
        val skill = origin()
        builder.invoke(skill)
        skill
    }
    SkillManager[id] = type
    return type
}