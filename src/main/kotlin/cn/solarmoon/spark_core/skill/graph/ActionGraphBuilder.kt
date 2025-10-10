package cn.solarmoon.spark_core.skill.graph

@DslMarker
annotation class ActionDsl

@ActionDsl
class ActionGraphBuilder {
    private val nodes = mutableMapOf<String, ActionNode>()
    private var initial: ActionNode? = null

    fun initialNode(
        id: String,
        exitCondition: ActionExitCondition = ActionExitCondition.False,
        block: ActionNodeBuilder.() -> Unit = {}
    ) {
        val node = node(id, exitCondition, block)
        initial = node
    }

    fun node(
        id: String,
        exitCondition: ActionExitCondition = ActionExitCondition.False,
        block: ActionNodeBuilder.() -> Unit = {}
    ): ActionNode {
        val builder = ActionNodeBuilder(id, exitCondition).apply(block)
        val node = builder.build()
        nodes[id] = node
        return node
    }

    fun build(): ActionGraph {
        val init = initial ?: error("必须至少定义一个初始节点")
        return ActionGraph(init, nodes)
    }
}

@ActionDsl
class ActionNodeBuilder(
    private val id: String,
    private val exitCondition: ActionExitCondition
) {
    private val transitions = mutableMapOf<String, ActionTransition>()

    fun onInput(input: String, nextId: String, condition: ActionCondition? = null) {
        transitions[input] = ActionTransition(id, nextId, condition)
    }

    fun build() = ActionNode(id, exitCondition, transitions)
}

fun actionGraph(block: ActionGraphBuilder.() -> Unit) = ActionGraphBuilder().apply(block).build()
