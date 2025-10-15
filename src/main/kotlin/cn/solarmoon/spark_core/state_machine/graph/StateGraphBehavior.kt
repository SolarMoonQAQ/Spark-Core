package cn.solarmoon.spark_core.state_machine.graph

interface StateGraphBehavior {
    fun onTriggered(event: StateGraphController.ActionEvent, targetNode: StateNode?, controller: StateGraphController) {}
    fun onEnter(controller: StateGraphController) {}
    fun onUpdate(controller: StateGraphController) {}
    fun onExit(controller: StateGraphController) {}
}
