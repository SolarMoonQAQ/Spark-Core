package cn.solarmoon.spark_core.behavior_tree

import cn.solarmoon.spark_core.behavior_tree.node.TreeNode

/**
 * This class holds the result for each executed node and its children
 *
 * @property origin the origin node of the result
 * @property status the status of the result
 * @property children the children of the result
 */
data class TreeNodeResult(
    val origin: TreeNode,
    val status: Status,
    val children: List<TreeNodeResult>? = null
) {
    fun stackTrace(indent: Int = 0): Sequence<String> = sequence {
        yield("${"\t".repeat(indent)}> ${origin.javaClass.simpleName} :${origin.name} ${status.name}")

        // Append children if they exist
        children?.forEach {
            yieldAll(it.stackTrace(indent + 1))
        }
    }

    companion object {
        fun success(parent: TreeNode, children: List<TreeNodeResult>? = null) = TreeNodeResult(parent, Status.SUCCESS, children)

        fun failure(parent: TreeNode, children: List<TreeNodeResult>? = null) = TreeNodeResult(parent, Status.FAILURE, children)
    }
}