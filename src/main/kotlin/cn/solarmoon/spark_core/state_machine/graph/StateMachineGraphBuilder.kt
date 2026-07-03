package cn.solarmoon.spark_core.state_machine.graph

import cn.solarmoon.spark_core.state_machine.graph.StateCondition.Reverse

@DslMarker
annotation class StateGraphDsl

@StateGraphDsl
interface NodeContainer {
    fun node(
        id: String,
        initial: Boolean,
        block: StateNodeBuilder.() -> Unit = {}
    ): StateNodeBuilder

    fun node(
        id: String,
        block: StateNodeBuilder.() -> Unit = {}
    ): StateNodeBuilder = node(id, false, block)

    fun initialNode(
        id: String,
        block: StateNodeBuilder.() -> Unit = {}
    ): StateNodeBuilder = node(id, true, block)
}

@StateGraphDsl
class StateMachineGraphBuilder : NodeContainer {
    private val nodes = mutableListOf<StateNodeBuilder>()
    private var initialNode: StateNodeBuilder? = null

    override fun node(id: String, initial: Boolean, block: StateNodeBuilder.() -> Unit) = StateNodeBuilder(id).apply(block).also { if (initial) initialNode = it else nodes += it }

    fun build(): StateMachineGraph {
        val init = initialNode ?: error("必须至少定义一个初始节点")
        return StateMachineGraph(init.build(), nodes.map { it.build() })
    }
}

@StateGraphDsl
class StateNodeBuilder(
    private val id: String
) : NodeContainer {
    private val transitions = mutableListOf<StateTransitionBuilder>()

    // 旧版隐式子图（向后兼容）：嵌套 node{} 自动组成 subGraphs[id]
    private val subNodes = mutableListOf<StateNodeBuilder>()
    private var initialNode: StateNodeBuilder? = null

    // 新版显式命名子图：subGraph("name") { ... } → subGraphs["name"]
    private val subGraphBuilders = mutableListOf<Pair<String, StateMachineGraphBuilder>>()

    // 进入/退出动作
    private val onEnterActions = mutableListOf<StateAction>()
    private val onExitActions = mutableListOf<StateAction>()

    fun on(event: String, block: StateTransitionBuilder.() -> Unit = {}) =
        StateTransitionBuilder(event).also {
            block(it)
            transitions.add(it)
        }

    override fun node(id: String, initial: Boolean, block: StateNodeBuilder.() -> Unit) =
        StateNodeBuilder(id).apply(block).also { if (initial) initialNode = it else subNodes += it }

    /**
     * 新建一个命名子图，构建块内使用 [StateMachineGraphBuilder] 的完整 DSL
     *（支持 initialNode、node、on、onEnter、onExit 等）。
     *
     * 用法：
     * ```
     * node("stand") {
     *     subGraph("gait") {
     *         initialNode("idle") { ... }
     *         node("walk") { ... }
     *     }
     *     subGraph("vert") {
     *         initialNode("ground") { ... }
     *     }
     * }
     * ```
     * 生成的 StateNode.subGraphs = {"gait": gaitGraph, "vert": vertGraph}
     *
     * @param name 子图名称，对应 [StateGraphController.children] 中的 key
     * @param block 子图构建块
     */
    fun subGraph(name: String, block: StateMachineGraphBuilder.() -> Unit) {
        subGraphBuilders.add(name to StateMachineGraphBuilder().apply(block))
    }

    // DSL: onEnter { +action }
    fun onEnter(block: ActionListBuilder.() -> Unit) {
        val builder = ActionListBuilder()
        builder.block()
        onEnterActions.addAll(builder.actions)
    }

    // DSL: onExit { +action }
    fun onExit(block: ActionListBuilder.() -> Unit) {
        val builder = ActionListBuilder()
        builder.block()
        onExitActions.addAll(builder.actions)
    }

    fun build(): StateNode {
        val subGraphs = mutableMapOf<String, StateMachineGraph>()

        // 1. 显式命名子图（新方式）
        subGraphBuilders.forEach { (name, builder) ->
            subGraphs[name] = builder.build()
        }

        // 2. 隐式子图：嵌套 node{} 自动组成 subGraphs[id]（旧方式，向后兼容）
        if (subNodes.isNotEmpty()) {
            check(initialNode != null) { "节点 \"$id\" 定义了子节点但缺少初始状态（initialNode），请设置一个子节点的 initial = true" }
            subGraphs[id] = StateMachineGraph(initialNode!!.build(), subNodes.map { it.build() })
        }

        return StateNode(
            id,
            transitions.map { it.build() },
            onEnterActions,
            onExitActions,
            subGraphs,
        )
    }
}

// 用于 DSL 的小 builder
@StateGraphDsl
class ActionListBuilder {
    internal val actions = mutableListOf<StateAction>()
    operator fun StateAction.unaryPlus() {
        actions.add(this)
    }
}

@StateGraphDsl
class StateTransitionBuilder(
    val event: String
) {
    var target: String? = null
    var condition: StateCondition = StateCondition.True

    infix fun go(target: String) = apply {
        this.target = target
    }

    infix fun ifCond(condition: StateCondition) = apply {
        this.condition = condition
    }

    fun build(): StateTransition {
        val target = target ?: error("必须指定目标节点")
        return StateTransition(event, target, condition)
    }
}

fun stateMachineGraph(block: StateMachineGraphBuilder.() -> Unit) = StateMachineGraphBuilder().apply(block).build()