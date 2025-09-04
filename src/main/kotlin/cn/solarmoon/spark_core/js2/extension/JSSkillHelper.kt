package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillConfig
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillManager
import cn.solarmoon.spark_core.skill.SkillStartCondition
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.skillType
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.Value
import org.mozilla.javascript.Context

object JSSkillHelper {

    private fun create(id: String, by: String? = null, consumer: Value) {
        val builder = Builder()
        builder.id = ResourceLocation.parse(id)
        consumer.execute(builder)
        if (by == null) builder.build() else builder.buildBy(SkillManager[ResourceLocation.parse(by)] ?: throw NullPointerException("父技能 $by 无法被继承，因为此技能尚未注册"))
    }

    fun create(id: String, consumer: Value) = create(id, null, consumer)

    fun createBy(id: String, by: String, consumer: Value) = create(id, by, consumer)

    class Builder {
        lateinit var id: ResourceLocation
        private val preFunctions = mutableListOf<(SkillConfig, Skill) -> Unit>()
        private val functions = mutableListOf<Skill.() -> Unit>()
        private val conditions = mutableListOf<SkillStartCondition>()

        fun getId() = id.toString()

        fun addCondition(id: String, reason: String, check: Value) {
            conditions.add(SkillStartCondition(id, reason) { host, level ->
                Context.toBoolean(check.execute(host, level))
            })
        }

        fun addEntityAnimatableCondition() {
            conditions.add(SkillStartCondition("need_entity_animatable", "技能持有者必须是实体且为动画体") { holder, level ->
                holder is IEntityAnimatable<*>
            } )
        }

        fun accept(consumer: Value) {
            functions.add {
                consumer.execute(this)
            }
        }

        fun acceptConfig(consumer: Value) {
            preFunctions.add { config, skill ->
                consumer.execute(config, skill)
            }
        }

        fun build(): SkillType<*> = skillType(id, conditions.toList()) {
            onEvent<SkillEvent.ConfigInit> {
                preFunctions.forEach { it.invoke(config, this) }
            }
            onEvent<SkillEvent.Init> {
                functions.forEach { it.invoke(this) }
            }
        }.apply {
            fromScript = true
        }

        fun buildBy(type: SkillType<*>) = skillType(id, conditions.toMutableList().apply { addAll(type.conditions) }, { type.provider() }) {
            onEvent<SkillEvent.ConfigInit> {
                preFunctions.forEach { it.invoke(config, this) }
            }
            onEvent<SkillEvent.Init> {
                functions.forEach { it.invoke(this) }
            }
        }.apply {
            fromScript = true
        }
    }

}

