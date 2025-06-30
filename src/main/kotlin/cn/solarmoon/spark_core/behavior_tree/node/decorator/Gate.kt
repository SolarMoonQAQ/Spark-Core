package cn.solarmoon.spark_core.behavior_tree.node.decorator

import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult
import cn.solarmoon.spark_core.behavior_tree.node.TreeNode

/**
 * A decorator node which executes its child only when a condition is met
 * Returns status of the child if executed, otherwise failure
 */
open class Gate(
    override val name: String,
    override val child: TreeNode,
    val validate: () -> Boolean
) : Decorator {
    override fun execute(): TreeNodeResult {
        if (validate()) {
            val result = child.execute()
            return TreeNodeResult(this, result.status, listOf(result))
        }

        return TreeNodeResult.failure(this)
    }
}
