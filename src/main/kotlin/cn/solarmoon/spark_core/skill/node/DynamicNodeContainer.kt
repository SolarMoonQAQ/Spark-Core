package cn.solarmoon.spark_core.skill.node

import cn.solarmoon.spark_core.skill.SkillInstance

class DynamicNodeContainer(
    private val parent: BehaviorNode
) {

    val children = mutableListOf<BehaviorNode>()

    fun addChild(node: BehaviorNode) {
        children.add(node)
        node.parent = parent
    }

    fun addChildren(nodes: List<BehaviorNode>) {
        children.addAll(nodes)
        nodes.forEach { it.parent = parent }
    }

    fun tick(skill: SkillInstance): NodeStatus {
        val iterator = children.iterator()
        while (iterator.hasNext()) {
            val child = iterator.next()
            when (child.tick(skill)) {
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.SUCCESS -> iterator.remove()
                NodeStatus.FAILURE -> iterator.remove()
            }
        }
        return if (children.isEmpty()) NodeStatus.SUCCESS else NodeStatus.RUNNING
    }

}