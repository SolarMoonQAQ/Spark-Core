package cn.solarmoon.spark_core.skill.graph

data class ActionGraph(
    val initialNode: ActionNode,
    val nodes: Map<String, ActionNode>,
) {



}
