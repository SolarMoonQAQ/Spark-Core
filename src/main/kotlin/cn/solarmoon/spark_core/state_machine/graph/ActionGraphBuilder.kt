package cn.solarmoon.spark_core.state_machine.graph

@DslMarker
annotation class ActionDsl

@ActionDsl
interface NodeContainer {
    fun node(
        id: String,
        block: ActionNodeBuilder.() -> Unit = {}
    ): ActionNodeBuilder

    fun initialNode(
        id: String,
        block: ActionNodeBuilder.() -> Unit = {}
    ): ActionNodeBuilder
}


@ActionDsl
class ActionGraphBuilder : NodeContainer {
    private val nodes = mutableMapOf<String, ActionNodeBuilder>()
    private var initial: ActionNodeBuilder? = null

    override fun initialNode(id: String, block: ActionNodeBuilder.() -> Unit) = node(id, block).also { initial = it }

    override fun node(id: String, block: ActionNodeBuilder.() -> Unit) = ActionNodeBuilder(id).apply(block).also { nodes[id] = it }

    fun build(): ActionGraph {
        val init = initial ?: error("必须至少定义一个初始节点")
        return ActionGraph(init.build(), nodes.mapValues { it.value.build() })
    }
}

@ActionDsl
class ActionNodeBuilder(
    private val id: String
) : NodeContainer {
    private val transitions = mutableListOf<ActionTransitionBuilder>()
    private val subNodes = mutableMapOf<String, ActionNodeBuilder>()
    private var initial: ActionNodeBuilder? = null

    fun on(event: String, block: ActionTransitionBuilder.() -> Unit = {}) = ActionTransitionBuilder(event, id).also {
        block(it)
        transitions.add(it)
    }

    override fun initialNode(id: String, block: ActionNodeBuilder.() -> Unit) = node(id, block).also { initial = it }

    override fun node(id: String, block: ActionNodeBuilder.() -> Unit) = ActionNodeBuilder(id).apply(block).also { subNodes[id] = it }

    fun build(): ActionNode {
        check(!(subNodes.isNotEmpty() && initial == null)) { "必须为子状态机设定一个初始状态" }
        return ActionNode(id, transitions.map { it.build() }, initial?.let { ActionGraph(it.build(), subNodes.mapValues { it.value.build() }) })
    }
}

@ActionDsl
class ActionTransitionBuilder(
    val event: String,
    val source: String
) {
    var target: String? = null
    var condition: ActionCondition = ActionCondition.True

    fun build(): ActionTransition {
        val target = target ?: error("必须指定目标节点")
        return ActionTransition(event, source, target, condition)
    }
}

infix fun ActionTransitionBuilder.go(target: String) = apply {
    this.target = target
}

infix fun ActionTransitionBuilder.ifCond(condition: ActionCondition) = apply {
    this.condition = condition
}

fun actionGraph(block: ActionGraphBuilder.() -> Unit) = ActionGraphBuilder().apply(block).build()