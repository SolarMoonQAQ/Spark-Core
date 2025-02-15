package cn.solarmoon.spark_core.skill.node.bases

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * ParallelNode 会在1tick内并行执行所有节点，并且只会反复执行当前状态为RUNNING的节点
 */
class ParallelNode(
    val children: List<BehaviorNode>,
    val policy: ParallelPolicy = ParallelPolicy.ALL_SUCCESS
) : BehaviorNode() {

    init {
        dynamicContainer.addChildren(children)
    }

    private val childStatusMap = mutableMapOf<BehaviorNode, NodeStatus>().apply {
        children.forEach { this[it] = NodeStatus.RUNNING }
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        var finalStatus: NodeStatus? = null

        children.forEach { child ->
            if (finalStatus != null) return@forEach

            // 仅未完成的节点需要执行
            if (childStatusMap[child] == NodeStatus.RUNNING) {
                val status = child.tick(skill)
                childStatusMap[child] = status

                // 策略判断是否需要提前终止
                when (policy) {
                    ParallelPolicy.ANY_FAILURE -> if (status == NodeStatus.FAILURE) finalStatus = NodeStatus.FAILURE
                    ParallelPolicy.ANY_SUCCESS -> if (status == NodeStatus.SUCCESS) finalStatus = NodeStatus.SUCCESS
                    else -> Unit
                }
            }
        }

        return finalStatus ?: calculateFinalStatus()
    }

    private fun calculateFinalStatus(): NodeStatus {
        return when (policy) {
            ParallelPolicy.ALL_SUCCESS -> if (childStatusMap.all { it.value == NodeStatus.SUCCESS }) NodeStatus.SUCCESS else NodeStatus.RUNNING
            ParallelPolicy.ANY_SUCCESS -> if (childStatusMap.any { it.value == NodeStatus.SUCCESS }) NodeStatus.SUCCESS else NodeStatus.RUNNING
            ParallelPolicy.ALL_FAILURE -> if (childStatusMap.all { it.value == NodeStatus.FAILURE }) NodeStatus.FAILURE else NodeStatus.RUNNING
            ParallelPolicy.ANY_FAILURE -> if (childStatusMap.any { it.value == NodeStatus.FAILURE }) NodeStatus.FAILURE else NodeStatus.RUNNING
        }
    }

    override fun onRefresh() {
        children.forEach {
            childStatusMap[it] = NodeStatus.RUNNING
        }
    }

    override fun copy() = ParallelNode(children.map { it.copy() }, policy)

    override val codec: MapCodec<out BehaviorNode> = CODEC

    companion object {
        val CODEC: MapCodec<ParallelNode> = RecordCodecBuilder.mapCodec {
            it.group(
                BehaviorNode.CODEC.listOf().fieldOf("children").forGetter { it.children },
                Codec.STRING.xmap({ParallelPolicy.valueOf(it.uppercase())}, {it.toString().lowercase()}).optionalFieldOf("policy", ParallelPolicy.ALL_SUCCESS).forGetter { it.policy }
            ).apply(it, ::ParallelNode)
        }
    }

}