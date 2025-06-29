package cn.solarmoon.spark_core.state_machine

interface Transition {

    val targetState: State

    fun match(): Boolean


}