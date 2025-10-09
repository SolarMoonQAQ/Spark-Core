package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.util.triggerEvent

interface SkillConfig {

    val storage: LinkedHashMap<String, Any>

    fun init(skill: Skill) {
        skill.triggerEvent(SkillEvent.ConfigInit(this))
    }

    fun set(id: String, value: Any) {
        storage[id] = value
    }

    fun set(pair: Map<String, Any>) {
        storage.putAll(pair)
    }

}

inline fun <reified T : Any> SkillConfig.read(key: String, defaultValue: T): T {
    var value = storage[key]
    if (defaultValue is Double && value is Int) value = value.toDouble()
    return when (value) {
        null -> defaultValue
        is T -> value
        else -> throw IllegalArgumentException(
            "技能配置参数[$key] 的类型必须为 ${T::class}，实际为：${value::class}"
        )
    }
}

inline fun <reified T : Any> SkillConfig.readNonNull(key: String): T {
    val value = storage[key]
    if (value == null) throw IllegalArgumentException("技能配置参数[$key] 不能为空")
    if (value !is T) throw IllegalArgumentException(
        "技能配置参数[$key] 的类型必须为 ${T::class}，实际为：${value::class}"
    )
    return value
}

inline fun <reified T : Any> SkillConfig.readNullable(key: String): T? {
    val value = storage[key]
    if (value == null) return null
    if (value !is T) throw IllegalArgumentException(
        "技能配置参数[$key] 的类型必须为 ${T::class}，实际为：${value::class}"
    )
    return value
}
