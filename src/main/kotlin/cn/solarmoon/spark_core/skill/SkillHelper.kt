package cn.solarmoon.spark_core.skill

import net.minecraft.resources.ResourceLocation

fun skillType(id: ResourceLocation, builder: Skill.() -> Unit): SkillType<Skill> = skillType(id, { Skill() }, builder)

inline fun <reified T: Skill> skillType(id: ResourceLocation, crossinline origin: () -> T, crossinline builder: T.() -> Unit): SkillType<T> {
    val type = SkillType(id) {
        val skill = origin()
        builder.invoke(skill)
        skill
    }
    SkillManager[id] = type
    return type
}