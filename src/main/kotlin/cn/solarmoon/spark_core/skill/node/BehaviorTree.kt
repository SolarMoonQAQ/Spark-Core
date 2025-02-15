package cn.solarmoon.spark_core.skill.node

import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.Codec
import java.util.concurrent.atomic.AtomicInteger

class BehaviorTree(
    val root: BehaviorNode
) {

    val allNodes = mutableListOf<BehaviorNode>()

    init {
        root.mountOnTree(this)
    }

    val blackBoard = BlackBoard()

    fun tick(skill: SkillInstance): NodeStatus {
        return root.tick(skill)
    }

    fun end(skill: SkillInstance) {
        root.end(skill)
    }

    fun copy(): BehaviorTree {
        return BehaviorTree(root.copy())
    }

    fun mountNode(node: BehaviorNode) {
        node.ordinal = allNodes.size
        allNodes.add(node)
    }

    companion object {
        val CODEC: Codec<BehaviorTree> = BehaviorNode.CODEC.xmap({ BehaviorTree(it) }, { it.root })
    }

}