package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.skill.ScriptSource
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillConfig
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.skillType
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.Function

class JSSkillTypeBuilder(val js: SparkJS) {

    lateinit var id: ResourceLocation
    var priority = 0
        private set
    private val preFunctions = mutableListOf<(SkillConfig, Skill) -> Unit>()
    private val functions = mutableListOf<Skill.() -> Unit>()

    /**
     * 当前正在处理的脚本文件名
     * 由JSSkillApi在处理脚本时设置
     */
    var currentFileName: String? = null

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
        preFunctions.add { config, skill ->
            consumer.call(js, config, skill)
        }
    }

    fun build(): SkillType<*> = skillType(id) {
        onEvent<SkillEvent.ConfigInit> {
            preFunctions.forEach { it.invoke(config, this) }
        }
        onEvent<SkillEvent.Init> {
            functions.forEach { it.invoke(this) }
        }
    }.apply {
        fromJS = true
        // 设置脚本来源信息
        scriptSource = currentFileName?.let { fileName ->
            ScriptSource("skill", fileName)
        }
    }

    fun buildBy(type: SkillType<*>) = skillType(id, { type.provider() }) {
        onEvent<SkillEvent.ConfigInit> {
            preFunctions.forEach { it.invoke(config, this) }
        }
        onEvent<SkillEvent.Init> {
            functions.forEach { it.invoke(this) }
        }
    }.apply {
        fromJS = true
        // 设置脚本来源信息
        scriptSource = currentFileName?.let { fileName ->
            ScriptSource("skill", fileName)
        }
    }

}