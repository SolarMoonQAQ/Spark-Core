package cn.solarmoon.spark_core.skill.graph

data class ActionTransition(
    val source: String,
    val target: String,
    val condition: ActionCondition? = null
)