package cn.solarmoon.spark_core.behavior_tree.node.decorator

import cn.solarmoon.spark_core.behavior_tree.Status
import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult
import cn.solarmoon.spark_core.behavior_tree.node.TreeNode

/**
 * A decorator node that repeatedly executes its child until the specified condition is met
 */
open class RepeatUntil(
    override val name: String,
    private val limit: Int,
    override val child: TreeNode,
    val validate: (result: TreeNodeResult) -> Boolean
) : Decorator {
    override fun execute(): TreeNodeResult {
        val results = mutableListOf<TreeNodeResult>()
        var iteration = 0

        var result: TreeNodeResult
        do {
            result = child.execute()
            results.add(result)

            iteration++
        } while (!validate(result) && result.status != Status.ABORT && iteration < limit)

        val status = if (iteration >= limit) {
            Status.FAILURE
        } else {
            result.status
        }

        return TreeNodeResult(this, status, results)
    }
}