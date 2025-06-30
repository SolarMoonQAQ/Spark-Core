package cn.solarmoon.spark_core.behavior_tree.node

import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult

/**
 * Interface for a node of an executable tree
 */
interface TreeNode {
    /**
     * A descriptive name for the tree node
     */
    val name: String

    /**
     * Executes a certain behaviour of the node
     * @return the result of the execution, including relevant children results
     */
    fun execute(): TreeNodeResult
}