package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entity.skill.Skill
import net.neoforged.neoforge.registries.DeferredRegister

class SkillBuilder<S: Skill<*>>(private val skillTypeRegister: DeferredRegister<Skill<*>>) {

    private var id: String = ""
    private var skill: (() -> S)? = null

    fun id(id: String) = apply { this.id = id }
    fun bound(skill: () -> S) = apply { this.skill = skill }

    fun build() = skillTypeRegister.register(id, skill!!)

}