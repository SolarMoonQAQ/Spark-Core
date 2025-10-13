package cn.solarmoon.spark_core.gas

fun gameplayTag(id: String): GameplayTag {
    return GameplayTag(id)
}

infix fun GameplayTag.attach(value: Any): GameplayTag {
    return GameplayTag(this.path + "." + value)
}