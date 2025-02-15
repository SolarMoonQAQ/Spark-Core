package cn.solarmoon.spark_core.skill.node.bases

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * SelectorNode 将在1tick内按顺序执行子节点，直到当前节点返回running或success为止，一般用于执行需要按优先级的条件操作
 *
 * 比如：
 * ```
 *           👉 在范围内(S/F) -> 攻击(SUCCESS)
 *
 * selector  👉 看到敌人(S/F) -> 追逐(RUNNING)
 *
 *           👉 游荡(RUNNING)
 * ```
 * 在这个例子中，首先会尝试进行攻击，如果不满足范围内的条件，则会一直尝试进行追逐，如果仍无法满足看到敌人的条件，则一直游荡
 */
class SelectorNode(
    val children: List<BehaviorNode>
) : BehaviorNode() {

    init {
        dynamicContainer.addChildren(children)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        children.forEach { child ->
            when (child.tick(skill)) {
                NodeStatus.SUCCESS -> return NodeStatus.SUCCESS
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.FAILURE -> Unit
            }
        }
        return NodeStatus.FAILURE
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return SelectorNode(children.map { it.copy() })
    }

    companion object {
        val CODEC: MapCodec<SelectorNode> = RecordCodecBuilder.mapCodec {
            it.group(
                BehaviorNode.CODEC.listOf().fieldOf("children").forGetter { it.children }
            ).apply(it, ::SelectorNode)
        }
    }

}