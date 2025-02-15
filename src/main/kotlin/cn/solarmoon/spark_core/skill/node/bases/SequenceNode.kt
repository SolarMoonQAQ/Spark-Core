package cn.solarmoon.spark_core.skill.node.bases

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * SequenceNode 将在1tick内按顺序执行子节点，但当前子节点必须返回SUCCESS，如果当前返回FAILURE，则会立刻从头开始执行，如果返回RUNNING，则会保留当前的节点序列，在下一tick中仍然从当前位置开始执行
 */
class SequenceNode(
    val children: List<BehaviorNode>
) : BehaviorNode() {

    private var currentIndex = 0

    init {
        dynamicContainer.addChildren(children)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        while (currentIndex < children.size) {
            when (children[currentIndex].tick(skill)) {
                NodeStatus.FAILURE -> {
                    reset()
                    return NodeStatus.FAILURE
                }
                NodeStatus.SUCCESS -> currentIndex++
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
            }
        }
        reset()
        return NodeStatus.SUCCESS
    }

    private fun reset() { currentIndex = 0 }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return SequenceNode(children.map { it.copy() })
    }

    companion object {
        val CODEC: MapCodec<SequenceNode> = RecordCodecBuilder.mapCodec {
            it.group(
                BehaviorNode.CODEC.listOf().fieldOf("children").forGetter { it.children }
            ).apply(it, ::SequenceNode)
        }
    }

}