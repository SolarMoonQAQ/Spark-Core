package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillConfig
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.skillType
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.Function

class JSSkillTypeBuilder(val js: SparkJS) {

    lateinit var id: ResourceLocation
    var priority = 0
        private set
    private val preFunctions = mutableListOf<SkillConfig.() -> Unit>()
    private val functions = mutableListOf<Skill.() -> Unit>()

    fun getId() = id.toString()

    fun setPriority(value: Int) {
        priority = value
    }

    fun accept(consumer: Function) {
        functions.add {
            consumer.call(js, this)
        }
    }

    fun acceptConfig(consumer: Function) {
        preFunctions.add {
            consumer.call(js, this)
        }
    }

    fun build(): SkillType<*> = skillType(id) {
        preFunctions.forEach { it.invoke(config) }
        init {
            functions.forEach { it.invoke(this) }
        }
    }.apply { fromJS = true }

    fun buildBy(type: SkillType<*>) = skillType(id, { type.provider() }) {
        preFunctions.forEach { it.invoke(config) }
        init {
            functions.forEach { it.invoke(this) }
        }
    }.apply { fromJS = true }

}