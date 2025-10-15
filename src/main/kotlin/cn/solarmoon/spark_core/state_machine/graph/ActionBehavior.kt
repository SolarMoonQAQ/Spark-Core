package cn.solarmoon.spark_core.state_machine.graph

interface ActionBehavior {
    fun onTriggered(event: ActionController.ActionEvent, targetNode: ActionNode?, controller: ActionController) {}
    fun onEnter(controller: ActionController) {}
    fun onUpdate(controller: ActionController) {}
    fun onExit(controller: ActionController) {}
}
