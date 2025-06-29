package cn.solarmoon.spark_core.behavior_tree.node.composite

import cn.solarmoon.spark_core.behavior_tree.ExecutionOrder
import cn.solarmoon.spark_core.behavior_tree.Status
import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult
import cn.solarmoon.spark_core.behavior_tree.node.TreeNode

/**
 * A composite node which executes its child nodes in order until one fails or all succeed, similar to an `and` operator
 *
 * @property order the order in which the children should be executed
 */
open class Sequencer(
    override val name: String,
    private val order: ExecutionOrder = ExecutionOrder.IN_ORDER,
    override val children: MutableList<TreeNode> = mutableListOf()
) : Composite {
    override fun execute(): TreeNodeResult {
        val children = if (order == ExecutionOrder.RANDOM) {
            children.shuffled()
        } else {
            children
        }

        val results = mutableListOf<TreeNodeResult>()

        for (child in children) {
            val result = child.execute()
            results.add(result)

            if (result.status == Status.FAILURE || result.status == Status.ABORT) {
                return TreeNodeResult(this, result.status, results)
            }
        }

        return TreeNodeResult.success(this, results)
    }
}
