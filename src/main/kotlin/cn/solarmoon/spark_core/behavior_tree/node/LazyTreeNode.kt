package cn.solarmoon.spark_core.behavior_tree.node

import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult

abstract class LazyTreeNode : TreeNode {
    private val node: TreeNode by lazy { build() }

    abstract fun build(): TreeNode

    override fun execute(): TreeNodeResult {
        return node.execute()
    }
}