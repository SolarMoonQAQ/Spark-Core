package cn.solarmoon.spark_core.state_machine

interface State {

    val name: String

    val transitions: MutableList<Transition>

    fun onEntry()

    fun onUpdate()

    fun onExit()

}