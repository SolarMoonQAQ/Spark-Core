package cn.solarmoon.spark_core.behavior_tree.node.composite

import cn.solarmoon.spark_core.behavior_tree.BehaviourTreeDslMarker
import cn.solarmoon.spark_core.behavior_tree.node.TreeNode

/**
 * This is an abstract class for a type of tree node called the composite node
 * Composite nodes are used to process one or more children in a sequence depending on the implementation
 *
 * @property name a descriptive name of the composites' usage
 */
@BehaviourTreeDslMarker
sealed interface Composite : TreeNode {
    val children: MutableList<TreeNode>

    operator fun TreeNode.unaryPlus() {
        children += this
    }

    operator fun TreeNode.unaryMinus() {
        children -= this
    }
}