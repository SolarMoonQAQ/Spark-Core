package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.gas.GameplayTagContainer
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.onExit
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transitionConditionally
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.statemachine.startBlocking
import ru.nsk.kstatemachine.statemachine.stopBlocking
import ru.nsk.kstatemachine.transition.noTransition
import ru.nsk.kstatemachine.transition.onTriggered
import ru.nsk.kstatemachine.transition.targetState

// 此处时间轴只用于控制输入缓冲
open class StateGraphController @JvmOverloads constructor(
    val stateMachineGraph: StateMachineGraph,
    /** 直接子控制器实例（key = 控制器名），工厂方法递归填充，运行时只含本层 */
    private val children: Map<String, StateGraphController> = mapOf(),
    /** 数值型状态变量容器（speed、input_forward 等）。可传入外部容器实现父子/跨层共享；null 则自建。 */
    variables: StateVariableContainer? = null,
    /** 标记型标签容器。可传入外部容器实现父子/跨层共享；null 则自建。 */
    tags: GameplayTagContainer? = null
) {

    /** 数值型状态变量容器。若构造时传入外部容器则共享引用，否则自建独立实例。 */
    val variables: StateVariableContainer = variables ?: StateVariableContainer()
    /** 标记型标签容器。若构造时传入外部容器则共享引用，否则自建独立实例。 */
    val tags: GameplayTagContainer = tags ?: GameplayTagContainer()

    /** 变量容器是否自建（非共享），reset 时用于判断是否 clear */
    private val ownsVariables = variables == null
    /** 标签容器是否自建（非共享），reset 时用于判断是否 clear */
    private val ownsTags = tags == null

    companion object {
        /** 兜底步长 (s)：20 TPS 一帧，仅作为 [lastDt] 的构造初值，正式调用必须传入真实 dt。 */
        const val DEFAULT_DT = 1f / 20f
    }

    var currentNode: StateNode = stateMachineGraph.initialNode
        private set

    /**
     * 当前节点驻留时间 (s)。节点进入时清零，每次 [progress] 累加传入的 dt。
     * 供 [StateTimeCondition] 等"有时长状态"（dodge / stun / hard_land）自动退出使用。
     */
    var stateTime: Float = 0f
        private set

    /**
     * 控制器累计运行时间 (s)，单调递增，不随节点切换/重置清零。
     * 供冷却（[ElapsedTimeCondition] + [RecordTimeAction]）、能量恢复等全局时钟使用。
     */
    var controllerTime: Float = 0f
        private set

    /** 最近一次 [progress] 传入的步长 (s)，供动作/条件查询。 */
    var lastDt: Float = DEFAULT_DT
        private set

    /** 当前状态激活的子控制器 */
    private val activeChildren = mutableMapOf<String, StateGraphController>()

    class ActionEvent(val type: String?): Event {
        var targetNode: StateNode? = null
    }

    /**
     * 延迟启动的状态机（start = false），构造后不自动进入初始节点。
     * 子控制器生命周期统一由 [enterNode]/[exitNode] 管理。
     */
    private val stateMachine = createStdLibStateMachine(start = false) {
        val states = mutableMapOf<String, IState>()
        val stateToNodes = mutableMapOf<IState, StateNode>()

        /**
         * 递归创建 KStateMachine 状态，注册统一的 enter/exit 回调。
         * 子图不内联到 KStateMachine，由 enterNode/exitNode 通过 activeChildren 管理。
         */
        fun IState.createStates(graph: StateMachineGraph) {
            graph.nodeMap.forEach { (id, node) ->
                val state = (if (id == graph.initialNode.name) initialState(graph.initialNode.name) else state(id)).apply {
                    onEntry {
                        enterNode(node)
                    }

                    onExit {
                        exitNode(node)
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
                                targetState(states[next.target]!!)
                            } else noTransition()
                        }
                        onTriggered {
                            // 在已确认转移的回调中写入目标节点，确保 targetNode 语义正确
                            val target = stateToNodes[it.direction.targetState]
                            it.event.targetNode = target
                            onTriggered(it.event, stateToNodes[it.transition.sourceState], target)
                        }
                    }
                }
                states[id] = state
                stateToNodes[state] = node
            }
        }

        createStates(stateMachineGraph)
    }

    // ═══════════════════════════════════════════════
    // 节点生命周期（统一 enter/exit，供 KStateMachine 回调和 stop() 共用）
    // ═══════════════════════════════════════════════

    /**
     * 进入节点：先执行 [StateNode.onEntry] actions，再激活子控制器，最后调用子类钩子。
     */
    private fun enterNode(node: StateNode) {
        stateTime = 0f // 节点驻留计时从进入当帧起算
        val previousNode = currentNode
        currentNode = node
        node.onEntry.forEach { it.execute(this) }

        // 先收集需要激活的子控制器映射（名称 → 实例），避免在 try 块内修改 activeChildren
        val toActivate = mutableMapOf<String, StateGraphController>()
        for (name in node.subGraphs.keys) {
            val child = children[name]
            if (child != null) {
                toActivate[name] = child
            } else {
                SparkCore.LOGGER.warn("子控制器 $name 未在 children 中注册")
            }
        }

        try {
            for (child in toActivate.values) {
                child.start()
            }
        } catch (e: Exception) {
            // 回滚：停止已激活的子控制器，还原 currentNode
            SparkCore.LOGGER.warn("激活子控制器失败，回滚节点 ${node.name} 的 entry", e)
            for (child in toActivate.values) {
                if (child.isStarted) {
                    try {
                        child.stop()
                    } catch (rollbackError: Exception) {
                        SparkCore.LOGGER.error("回滚子控制器时出错", rollbackError)
                    }
                }
            }
            // activeChildren 此时尚未写入，无需清理
            currentNode = previousNode
            throw e
        }

        // 全部成功后才写入 activeChildren
        activeChildren.putAll(toActivate)

        onEntry(node) // 子类钩子，不再负责激活 children
    }

    /**
     * 退出节点：按内到外顺序 — 先完整停止活跃子图，再执行 [StateNode.onExit] actions，
     * 最后调用子类 [onExit] 钩子。
     */
    private fun exitNode(node: StateNode) {
        // 内到外：先停止活跃子图；单个子控停止失败不影响其余子图的停止
        val errors = mutableListOf<Exception>()
        for (child in activeChildren.values) {
            try {
                child.stop()
            } catch (e: Exception) {
                SparkCore.LOGGER.warn("退出节点 ${node.name} 时停止子控制器出错", e)
                errors.add(e)
            }
        }
        activeChildren.clear()

        node.onExit.forEach { it.execute(this) }
        onExit(node) // 子类钩子，不再负责递归退出 children

        if (errors.isNotEmpty()) {
            throw RuntimeException(
                "退出节点 ${node.name} 时 ${errors.size} 个子控制器停止失败",
                errors.first()
            )
        }
    }

    // ═══════════════════════════════════════════════
    // 公开生命周期入口
    // ═══════════════════════════════════════════════

    /** 底层 KStateMachine 是否已启动。构造完成后为 false，需显式调用 [start]。 */
    val isStarted: Boolean
        get() = stateMachine.isRunning

    /**
     * 启动控制器：进入 [stateMachineGraph.initialNode]。
     *
     * @throws IllegalStateException 控制器已启动
     */
    fun start() {
        check(!stateMachine.isRunning) { "StateGraphController 已启动" }
        stateMachine.startBlocking()
    }

    /**
     * 停止控制器：先退出当前节点（含子图清理），再停止底层状态机。
     */
    fun stop() {
        if (!stateMachine.isRunning) return
        exitNode(currentNode)
        stateMachine.stopBlocking()
    }

    /**
     * 重置到初始状态（递归子控）。
     * 构造完成后初次调用即可正常初始化——无需额外调用 [start]。
     *
     * 共享 storage 时不 clear——避免子控 reset 误清父控/外部写入的快照数据。
     */
    open fun reset() {
        if (stateMachine.isRunning) stop()

        // 只清理由本控制器自己创建的容器，在初始节点 entry 之前清理
        if (ownsTags) tags.clear()
        if (ownsVariables) variables.clear()

        start()
    }

    // ═══════════════════════════════════════════════
    // 每帧推进
    // ═══════════════════════════════════════════════

    /**
     * 每帧调用。先递归驱动子控制器，再驱动自身 event=null 转移。
     *
     * @param dt 本帧物理步长 (s)，变 TPS 下必须传入真实秒数；同时累积
     *           [stateTime]（节点驻留）与 [controllerTime]（全局时钟）。
     */
    open fun progress(dt: Float) {
        check(isStarted) { "StateGraphController 未启动，请先调用 start() 或 reset()" }
        lastDt = dt
        controllerTime += dt
        stateTime += dt
        activeChildren.values.forEach { it.progress(dt) }
        triggerEvent(null)
    }

    // ═══════════════════════════════════════════════
    // 事件 API
    // ═══════════════════════════════════════════════

    /**
     * 在当前控制器上触发事件，只处理本层转移。
     * 使用 [broadcastEvent] 将事件广播到整棵活跃树。
     */
    open fun triggerEvent(type: String?): ActionEvent {
        check(isStarted) { "StateGraphController 未启动，请先调用 start() 或 reset()" }
        val event = ActionEvent(type)
        stateMachine.processEventBlocking(event)
        return event
    }

    /**
     * 将非空事件从当前控制器向其处理后的活跃子树广播。
     * 顺序：父级优先、深度优先；不消费、不短路。
     *
     * 父级先处理事件，确定本次事件后的模式与活跃子树；
     * 再将同一个事件向下传播给处理后的活跃子控制器。
     * 由父级转移退出的旧子图已从 [activeChildren] 移除，不会收到事件；
     * 父级转移新激活的子图会继续收到同一个事件。
     *
     * @param type 非空事件名
     */
    open fun broadcastEvent(type: String) {
        triggerEvent(type)
        for (child in activeChildren.values) {
            child.broadcastEvent(type)
        }
    }

    // ═══════════════════════════════════════════════
    // 子类钩子
    // ═══════════════════════════════════════════════

    open fun onEntry(node: StateNode) {}

    open fun onExit(node: StateNode) {}

    open fun onTriggered(event: ActionEvent, source: StateNode?, target: StateNode?) {
        SparkCore.LOGGER.info("执行动作: ${target?.name}")
    }

    open fun onCheckTransition() {}

}
