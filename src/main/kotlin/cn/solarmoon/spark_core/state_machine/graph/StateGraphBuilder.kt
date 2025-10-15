package cn.solarmoon.spark_core.state_machine.graph

@DslMarker
annotation class StateGraphDsl

@StateGraphDsl
interface NodeContainer {
    fun node(
        id: String,
        block: StateNodeBuilder.() -> Unit = {}
    ): StateNodeBuilder

    fun initialNode(
        id: String,
        block: StateNodeBuilder.() -> Unit = {}
    ): StateNodeBuilder
}

@StateGraphDsl
class StateGraphBuilder : NodeContainer {
    private val nodes = mutableMapOf<String, StateNodeBuilder>()
    private var initial: StateNodeBuilder? = null

    override fun initialNode(id: String, block: StateNodeBuilder.() -> Unit) = node(id, block).also { initial = it }

    override fun node(id: String, block: StateNodeBuilder.() -> Unit) = StateNodeBuilder(id).apply(block).also { nodes[id] = it }

    fun build(): StateGraph {
        val init = initial ?: error("必须至少定义一个初始节点")
        return StateGraph(init.build(), nodes.mapValues { it.value.build() })
    }
}

@StateGraphDsl
class StateNodeBuilder(
    private val id: String
) : NodeContainer {
    private val transitions = mutableListOf<StateTransitionBuilder>()
    private val subNodes = mutableMapOf<String, StateNodeBuilder>()
    private var initial: StateNodeBuilder? = null

    fun on(event: String, block: StateTransitionBuilder.() -> Unit = {}) = StateTransitionBuilder(event, id).also {
        block(it)
        transitions.add(it)
    }

    override fun initialNode(id: String, block: StateNodeBuilder.() -> Unit) = node(id, block).also { initial = it }

    override fun node(id: String, block: StateNodeBuilder.() -> Unit) = StateNodeBuilder(id).apply(block).also { subNodes[id] = it }

    fun build(): StateNode {
        check(!(subNodes.isNotEmpty() && initial == null)) { "必须为子状态机设定一个初始状态" }
        return StateNode(id, transitions.map { it.build() }, initial?.let { StateGraph(it.build(), subNodes.mapValues { it.value.build() }) })
    }
}

@StateGraphDsl
class StateTransitionBuilder(
    val event: String,
    val source: String
) {
    var target: String? = null
    var condition: StateCondition = StateCondition.True

    fun build(): StateTransition {
        val target = target ?: error("必须指定目标节点")
        return StateTransition(event, source, target, condition)
    }
}

infix fun StateTransitionBuilder.go(target: String) = apply {
    this.target = target
}

infix fun StateTransitionBuilder.ifCond(condition: StateCondition) = apply {
    this.condition = condition
}

fun stateGraph(block: StateGraphBuilder.() -> Unit) = StateGraphBuilder().apply(block).build()