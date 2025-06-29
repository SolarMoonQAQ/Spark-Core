package cn.solarmoon.spark_core.behavior_tree.node.decorator

import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult
import cn.solarmoon.spark_core.behavior_tree.node.TreeNode

/**
 * A decorator node that turns the result of its child into success, regardless of what the child returns
 */
class Succeeder(
    override val name: String,
    override val child: TreeNode
) : Decorator {
    override fun execute(): TreeNodeResult {
        val result = child.execute()
        return TreeNodeResult.success(this, listOf(result))
    }
}