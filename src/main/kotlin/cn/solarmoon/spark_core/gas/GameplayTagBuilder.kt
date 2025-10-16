package cn.solarmoon.spark_core.gas

fun String.toGameplayTag(): GameplayTag {
    return GameplayTag(this)
}

infix fun GameplayTag.attach(value: Any): GameplayTag {
    return GameplayTag(this.path + "." + value)
}