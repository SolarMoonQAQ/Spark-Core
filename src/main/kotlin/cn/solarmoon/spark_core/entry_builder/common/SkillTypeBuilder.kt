package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillType
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class SkillTypeBuilder<T, S: Skill<T>>(private val modId: String, private val skillTypeRegister: DeferredRegister<SkillType<*, *>>) {

    private var id: String = ""
    private var skill: ((T, SkillType<T, S>) -> S)? = null

    fun id(id: String) = apply { this.id = id }
    fun bound(skill: (T, SkillType<T, S>) -> S) = apply { this.skill = skill }

    fun build() = skillTypeRegister.register(id, Supplier { SkillType(ResourceLocation.fromNamespaceAndPath(modId, id), skill!!) })

}