package cn.solarmoon.spark_core.state_control

abstract class StateMachine<T>(
    val holder: T
) {

    var currentState: ObjectState<T>? = null
        private set
    var lastState: ObjectState<T>? = null
        private set

    open fun setState(state: ObjectState<T>?) {
        lastState = currentState
        currentState = state
        if (lastState != state) onStateChanged(lastState, state)
    }

    abstract fun onStateChanged(oldState: ObjectState<T>?, newState: ObjectState<T>?)

    abstract fun handleState()


}