package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.skillType
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

class JSSkillTypeBuilder {

    lateinit var id: ResourceLocation
    var priority = 0
        private set
    private val functions = mutableListOf<Skill.() -> Unit>()

    @HostAccess.Export
    fun getId() = id.toString()

    @HostAccess.Export
    fun setPriority(value: Int) {
        priority = value
    }

    @HostAccess.Export
    fun accept(consumer: Value) {
        functions.add {
            consumer.execute(jsSkill)
        }
    }

    fun build(): SkillType<*> = skillType(id) {
        init {
            functions.forEach { it.invoke(this) }
        }
    }.apply { fromJS = true }

    fun buildBy(type: SkillType<*>) = skillType(id, { type.provider() }) {
        init {
            functions.forEach { it.invoke(this) }
        }
    }.apply { fromJS = true }

}