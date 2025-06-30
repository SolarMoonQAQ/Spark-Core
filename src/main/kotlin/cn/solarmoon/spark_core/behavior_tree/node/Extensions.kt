package cn.solarmoon.spark_core.behavior_tree.node

import cn.solarmoon.spark_core.behavior_tree.node.decorator.Inverter

fun TreeNode.inverted() = Inverter(name, this)