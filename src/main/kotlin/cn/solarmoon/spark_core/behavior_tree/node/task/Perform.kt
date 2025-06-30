package cn.solarmoon.spark_core.behavior_tree.node.task

import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult

/**
 * An action node which performs an action and always returns a successful resul
 */
abstract class Perform(
    override val name: String,
) : Task {
    abstract fun action()

    override fun execute(): TreeNodeResult {
        action()
        return TreeNodeResult.success(this)
    }
}
