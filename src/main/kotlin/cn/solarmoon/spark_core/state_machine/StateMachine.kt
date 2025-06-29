package cn.solarmoon.spark_core.state_machine

interface StateMachine {

    val initialState: State

    var currentState: State?

    fun progress() {
        transitionTo(initialState)

    }

    fun transitionTo(targetState: State) {
        currentState?.onExit()
        currentState = targetState
        currentState?.onEntry()
    }

}