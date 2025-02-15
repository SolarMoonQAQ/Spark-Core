package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

class EndSkillNode: BehaviorNode() {

    override fun onTick(skill: SkillInstance): NodeStatus {
        skill.end()
        return NodeStatus.SUCCESS
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return EndSkillNode()
    }

    companion object {
        val CODEC: MapCodec<EndSkillNode> = RecordCodecBuilder.mapCodec { it.stable(EndSkillNode()) }
    }

}