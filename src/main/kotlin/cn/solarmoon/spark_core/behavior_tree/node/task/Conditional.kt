package cn.solarmoon.spark_core.behavior_tree.node.task

import cn.solarmoon.spark_core.behavior_tree.Status
import cn.solarmoon.spark_core.behavior_tree.TreeNodeResult

/**
 * An action node which turns a boolean result into a status, with true being successful and false being failure
 */
abstract class Conditional(
    override val name: String,
) : Task {
    abstract fun validate(): Boolean

    override fun execute(): TreeNodeResult = TreeNodeResult(this, Status.fromCondition(validate()))
}