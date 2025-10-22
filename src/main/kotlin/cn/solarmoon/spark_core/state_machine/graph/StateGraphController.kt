package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.GameplayTagContainer
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.onTriggered
import ru.nsk.kstatemachine.transition.targetState

// 此处时间轴只用于控制输入缓冲
open class StateGraphController(
    val stateMachineGraph: StateMachineGraph,
) {

    val tags = GameplayTagContainer()
    var currentNode: StateNode = stateMachineGraph.initialNode
        private set

    class ActionEvent(val type: String): Event {
        var targetNode: StateNode? = null
    }

    private val stateMachine = createStdLibStateMachine {
        val states = mutableMapOf<String, IState>()
        val stateToNodes = mutableMapOf<IState, StateNode>()
        fun IState.createStates(graph: StateMachineGraph) {
            graph.nodeMap.forEach { (id, node) ->
                val state = (if (id == graph.initialNode.name) initialState(graph.initialNode.name) else state(id)).apply {
                    onEntry {
                        currentNode = node
                        node.onEntry.forEach { it.execute(this@StateGraphController) }
                        onEntry(node)
                    }

                    onExit {
                        node.onExit.forEach { it.execute(this@StateGraphController) }
                        onExit(node)
                    }

                    // 输入事件驱动衔接
                    transitionConditionally<ActionEvent> {
                        direction = {
                            onCheckTransition()
                            val next = node.transitionMap[event.type]?.firstOrNull { it.condition.check(this@StateGraphController) }
                            if (next != null) {
                                event.targetNode = node
                                targetState(states[next.target]!!)
                            } else noTransition()
                        }
                        onTriggered {
                            onTriggered(it.event, stateToNodes[it.transition.sourceState], stateToNodes[it.direction.targetState])
                        }
                    }

                    node.subGraph?.let { createStates(it) }
                }
                states[id] = state
                stateToNodes[state] = node
            }
        }

        createStates(stateMachineGraph)
    }

    open fun triggerEvent(type: String): ActionEvent {
        val event = ActionEvent(type)
        stateMachine.processEventBlocking(event)
        return event
    }

    open fun onEntry(node: StateNode) {}

    open fun onExit(node: StateNode) {}

    open fun onTriggered(event: ActionEvent, source: StateNode?, target: StateNode?) {
        SparkCore.LOGGER.info("执行动作: ${target?.name}")
    }

    open fun onCheckTransition() {}

}

