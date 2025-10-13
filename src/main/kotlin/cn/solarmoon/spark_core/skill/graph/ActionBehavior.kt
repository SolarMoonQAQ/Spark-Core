package cn.solarmoon.spark_core.skill.graph

import cn.solarmoon.spark_core.skill.graph.ActionController.ActionEvent

interface ActionBehavior {
    fun onTriggered(event: ActionEvent, targetNode: ActionNode?, controller: ActionController) {}
    fun onEnter(controller: ActionController) {}
    fun onUpdate(controller: ActionController) {}
    fun onExit(controller: ActionController) {}
}
