package cn.solarmoon.spark_core.skill.graph

data class ActionNode(
    val id: String,
    val exitCondition: ActionExitCondition,
    val transitions: Map<String, ActionTransition> // 输入 -> 下一个动作
) {



}