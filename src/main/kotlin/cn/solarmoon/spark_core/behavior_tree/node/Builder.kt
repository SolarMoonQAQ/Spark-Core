package cn.solarmoon.spark_core.behavior_tree.node

import cn.solarmoon.spark_core.behavior_tree.ExecutionOrder
import cn.solarmoon.spark_core.behavior_tree.Status
import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult
import cn.solarmoon.spark_core.behavior_tree.node.composite.Selector
import cn.solarmoon.spark_core.behavior_tree.node.composite.Sequencer
import cn.solarmoon.spark_core.behavior_tree.node.decorator.Gate
import cn.solarmoon.spark_core.behavior_tree.node.decorator.Inverter
import cn.solarmoon.spark_core.behavior_tree.node.decorator.RepeatUntil
import cn.solarmoon.spark_core.behavior_tree.node.decorator.RepeatWhen
import cn.solarmoon.spark_core.behavior_tree.node.decorator.Succeeder
import cn.solarmoon.spark_core.behavior_tree.node.task.Action
import cn.solarmoon.spark_core.behavior_tree.node.task.Conditional
import cn.solarmoon.spark_core.behavior_tree.node.task.Perform

/**
 * Task nodes
 */
fun run(
    name: String = "",
    action: () -> Status,
) = object : Action(name) {
    override fun action(): Status = action()
}

fun conditional(
    name: String = "",
    validate: () -> Boolean,
) = object : Conditional(name) {
    override fun validate(): Boolean = validate()
}

fun perform(
    name: String = "",
    action: () -> Unit,
) = object : Perform(name) {
    override fun action() = action()
}

/**
 * Decorator nodes
 */
fun gate(
    name: String = "",
    validate: () -> Boolean,
    init: () -> TreeNode,
) = Gate(name, init(), validate)

fun inverter(
    name: String = "",
    init: () -> TreeNode,
) = Inverter(name, init())

fun succeeder(
    name: String = "",
    init: () -> TreeNode,
) = Succeeder(name, init())

fun repeatWhen(
    name: String = "",
    validate: () -> Boolean,
    limit: Int = 10,
    init: () -> TreeNode,
) = RepeatWhen(name, limit, init(), validate)

fun repeatUntil(
    validate: (TreeNodeResult) -> Boolean,
    limit: Int = 10,
    name: String = "",
    init: () -> TreeNode,
) = RepeatUntil(name, limit, init(), validate)

fun repeatUntil(
    status: Status,
    limit: Int = 10,
    name: String = "",
    init: () -> TreeNode,
) = RepeatUntil(name, limit, init()) { it.status == status }

/**
 * Composite nodes
 */

fun selector(
    name: String = "",
    executionOrder: ExecutionOrder = ExecutionOrder.IN_ORDER,
    init: Selector.() -> Unit,
) = initNode(Selector(name, executionOrder), init)

fun sequencer(
    name: String = "",
    executionOrder: ExecutionOrder = ExecutionOrder.IN_ORDER,
    init: Sequencer.() -> Unit,
) = initNode(Sequencer(name, executionOrder), init)

internal fun <T : TreeNode> initNode(node: T, init: T.() -> Unit): T {
    node.init()
    return node
}