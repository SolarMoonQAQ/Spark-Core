package cn.solarmoon.spark_core.skill

interface SkillConfig {

    val skill: Skill

    val storage: LinkedHashMap<String, Any>

    fun init() {}

    fun set(id: String, value: Any) {
        storage[id] = value
    }

}

inline fun <reified T: Any> SkillConfig.read(key: String, defaultValue: T): T {
    val value = storage.get(key)
    if (defaultValue is Number && defaultValue !is Double) throw IllegalArgumentException("由于js的数字类型限制为double，因此配置参数[$key] 的类型不能为 ${defaultValue::class.simpleName}，请用double类型填写然后转为想要的类型。")
    return when (value) {
        null -> defaultValue
        is T -> value
        else -> throw IllegalArgumentException("技能配置参数[$key] 的类型必须为 ${T::class.simpleName}")
    }
}

inline fun <reified T: Any> SkillConfig.readNonNull(key: String): T {
    val value = storage[key]
    if (value == null) throw IllegalArgumentException("技能配置参数[$key] 不能为空")
    if (value !is T) throw IllegalArgumentException("技能配置参数[$key] 的类型必须为 ${T::class.simpleName}")
    return value
}