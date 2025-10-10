package cn.solarmoon.spark_core.skill.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.input.InputBuffer
import cn.solarmoon.spark_core.skill.input.InputEvent
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.State
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.onTriggered
import ru.nsk.kstatemachine.transition.targetState
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// 此处时间轴只用于控制输入缓冲
class ActionController(
    val host: SkillHost,
    val actionGraph: ActionGraph
) {

    val buffer = InputBuffer()
    var currentNode: ActionNode = actionGraph.initialNode
        private set
    var tickCount = 0
        private set

    sealed class ActionEvent: Event {
        class Tick : ActionEvent()
        data class Input(val type: String) : ActionEvent()
    }

    private val stateMachine = createStdLibStateMachine {
        val initState = initialState(actionGraph.initialNode.id)

        val states = mutableMapOf<String, State>()
        actionGraph.nodes.forEach { (id, node) ->
            val state = (if (id == actionGraph.initialNode.id) initState else state(id)).apply {
                onEntry { currentNode = node }

                // Tick 事件驱动退出
                transition<ActionEvent.Tick> {
                    guard = { node.exitCondition.check(this@ActionController) }
                    targetState = initState
                    onTriggered { SparkCore.LOGGER.info("${node.id} 结束 → ${initState.name}") }
                }

                // 输入事件驱动衔接
                transitionConditionally<ActionEvent.Input> {
                    direction = {
                        val next = node.transitions[event.type]
                        val nextId = next?.target
                        val cond = next?.condition
                        if (nextId != null && (cond == null || cond.check(this@ActionController))) {
                            targetState(states[nextId]!!)
                        } else noTransition()
                    }
                    onTriggered {
                        SparkCore.LOGGER.info("执行动作: ${it.direction.targetState?.name}")
                    }
                }
            }
            states[id] = state
        }
    }

    fun onInput(type: String, duration: Int = 5, priority: Int = 0) {
        buffer.push(InputEvent(type, duration, tickCount, priority))
        tryConsume()
    }

    fun tick() {
        tickCount++
        stateMachine.processEventBlocking(ActionEvent.Tick())
        tryConsume()
    }

    private fun tryConsume() {
        val now = tickCount
        val input = buffer.popValid(now) ?: return
        stateMachine.processEventBlocking(ActionEvent.Input(input.type))
    }

}

