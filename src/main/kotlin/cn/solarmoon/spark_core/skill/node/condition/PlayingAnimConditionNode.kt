package cn.solarmoon.spark_core.skill.node.condition

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec

class PlayingAnimConditionNode(
    val needPlayingList: Set<String>
): BehaviorNode() {

    override fun onTick(skill: SkillInstance): NodeStatus {
        TODO("Not yet implemented")
    }

    override val codec: MapCodec<out BehaviorNode>
        get() = TODO("Not yet implemented")

    override fun copy(): BehaviorNode {
        TODO("Not yet implemented")
    }

}