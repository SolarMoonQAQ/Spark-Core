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
    private val subNodes = mutableListOf<StateNodeBuilder>()
    private var initialNode: StateNodeBuilder? = null

    // 新增：进入/退出动作
    private val onEnterActions = mutableListOf<StateAction>()
    private val onExitActions = mutableListOf<StateAction>()

    fun on(event: String, block: StateTransitionBuilder.() -> Unit = {}) =
        StateTransitionBuilder(event).also {
            block(it)
            transitions.add(it)
        }

    override fun node(id: String, initial: Boolean, block: StateNodeBuilder.() -> Unit) =
        StateNodeBuilder(id).apply(block).also { if (initial) initialNode = it else subNodes += it }

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
        check(!(subNodes.isNotEmpty() && initialNode == null)) { "必须为子状态机设定一个初始状态" }
        return StateNode(
            id,
            transitions.map { it.build() },
            onEnterActions,
            onExitActions,
            initialNode?.let { StateMachineGraph(it.build(), subNodes.map { it.build() }) },
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