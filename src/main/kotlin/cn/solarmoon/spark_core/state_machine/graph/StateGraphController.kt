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
import ru.nsk.kstatemachine.statemachine.restartBlocking
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.onTriggered
import ru.nsk.kstatemachine.transition.targetState

// 此处时间轴只用于控制输入缓冲
open class StateGraphController(
    val stateMachineGraph: StateMachineGraph,
    /** 直接子控制器实例（key = 控制器名），工厂方法递归填充，运行时只含本层 */
    private val children: Map<String, StateGraphController> = mapOf()
) {

    val tags = GameplayTagContainer()
    var currentNode: StateNode = stateMachineGraph.initialNode
        private set

    /** 当前状态激活的子控制器 */
    private val activeChildren = mutableMapOf<String, StateGraphController>()

    class ActionEvent(val type: String?): Event {
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

                    // 输入事件驱动衔接 + 无事件自动转移
                    transitionConditionally<ActionEvent> {
                        direction = {
                            onCheckTransition()
                            val next = if (event.type == null) {
                                // Bedrock 模式：只遍历 autoTransitions（通常 1~3 条）
                                node.autoTransitions.firstOrNull { it.condition.check(this@StateGraphController) }
                            } else {
                                // 事件驱动模式：O(1) Map 查找（保持原有性能）
                                node.eventTransitions[event.type]?.firstOrNull { it.condition.check(this@StateGraphController) }
                            }
                            if (next != null) {
                                event.targetNode = node
                                targetState(states[next.target]!!)
                            } else noTransition()
                        }
                        onTriggered {
                            onTriggered(it.event, stateToNodes[it.transition.sourceState], stateToNodes[it.direction.targetState])
                        }
                    }

                    // subGraphs 不再内联到 KStateMachine；由 onEntry 中的 children + activeChildren 管理
                }
                states[id] = state
                stateToNodes[state] = node
            }
        }

        createStates(stateMachineGraph)
    }

    /** 每帧调用。先递归驱动子控制器，再驱动自身 event=null 转移 */
    open fun progress() {
        activeChildren.values.forEach { it.progress() }
        triggerEvent(null)
    }

    /** 重置到初始状态（递归子控）。利用 KStateMachine.restartBlocking() 回到 initial state */
    open fun reset() {
        activeChildren.values.forEach { it.reset() }
        activeChildren.clear()
        stateMachine.restartBlocking()
        tags.clear()
    }

    open fun triggerEvent(type: String?): ActionEvent {
        val event = ActionEvent(type)
        stateMachine.processEventBlocking(event)
        return event
    }

    open fun onEntry(node: StateNode) {
        // 激活当前状态的子控制器（reset 确保每次进入从初始态开始）
        node.subGraphs.keys.forEach { name ->
            children[name]?.also {
                it.reset()
                activeChildren[name] = it
            }
        }
    }

    open fun onExit(node: StateNode) {
        // 递归退出子控制器——子控自己的 onExit 会清理孙子，逐层传递
        activeChildren.values.forEach { it.onExit(it.currentNode) }
        activeChildren.clear()
    }

    open fun onTriggered(event: ActionEvent, source: StateNode?, target: StateNode?) {
        SparkCore.LOGGER.info("执行动作: ${target?.name}")
    }

    open fun onCheckTransition() {}

}
