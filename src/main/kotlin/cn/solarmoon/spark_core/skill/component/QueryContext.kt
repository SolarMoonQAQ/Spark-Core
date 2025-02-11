package cn.solarmoon.spark_core.skill.component

class QueryContext {

    val data = mutableMapOf<String, Any>()

    operator fun get(path: String): Any? {
        return data[path]
    }

    operator fun set(name: String, value: Any) {
        data[name] = value
    }

}