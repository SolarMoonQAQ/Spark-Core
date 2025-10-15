package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.input_buffer.InputBuffer
import cn.solarmoon.spark_core.skill.input_buffer.InputBufferTriggerMode
import cn.solarmoon.spark_core.skill.input_buffer.InputEvent
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.onTriggered
import ru.nsk.kstatemachine.transition.targetState
import kotlin.collections.get

// 此处时间轴只用于控制输入缓冲
class ActionController(
    val actionGraph: ActionGraph,
    val behavior: ActionBehavior
) {

    val inputBuffer = InputBuffer()
    var inputTriggerMode = InputBufferTriggerMode.LAST_INPUT
    var currentNode: ActionNode = actionGraph.initialNode
        private set
    var tickCount = 0
        private set

    open class ActionEvent(val type: String): Event {
        data class Input(val value: InputEvent) : ActionEvent(value.type)
    }

    private val stateMachine = createStdLibStateMachine {
        val states = mutableMapOf<String, IState>()
        val stateToNodes = mutableMapOf<IState, ActionNode>()
        fun IState.createStates(graph: ActionGraph) {
            val initState = initialState(graph.initialNode.id)
            graph.nodes.forEach { (id, node) ->
                val state = (if (id == graph.initialNode.id) initState else state(id)).apply {
                    onEntry {
                        currentNode = node
                        behavior.onEnter(this@ActionController)
                    }

                    onExit {
                        behavior.onExit(this@ActionController)
                    }

                    // 输入事件驱动衔接
                    transitionConditionally<ActionEvent> {
                        direction = {
                            val next = node.transitionMap[event.type]?.firstOrNull { it.condition.check(this@ActionController) }
                            if (next != null) {
                                if (event is ActionEvent.Input) inputBuffer.pop(event.value)
                                targetState(states[next.target]!!)
                            } else noTransition()
                        }
                        onTriggered {
                            behavior.onTriggered(it.event, stateToNodes[it.direction.targetState], this@ActionController)
                            SparkCore.LOGGER.info("执行动作: ${it.direction.targetState?.name}")
                        }
                    }

                    node.subGraph?.let { createStates(it) }
                }
                states[id] = state
                stateToNodes[state] = node
            }
        }

        createStates(actionGraph)
    }

    fun triggerEvent(type: String) {
        stateMachine.processEventBlocking(ActionEvent(type))
    }

    fun pushInput(type: String, duration: Int = 5, priority: Int = 0) {
        inputBuffer.push(InputEvent(type, duration, tickCount, priority))
        tryConsume()
    }

    fun tick() {
        behavior.onUpdate(this)
        tickCount++
        tryConsume()
    }

    private fun tryConsume() {
        val input = inputBuffer.peekValid(tickCount, inputTriggerMode) ?: return
        stateMachine.processEventBlocking(ActionEvent.Input(input))
    }

}

