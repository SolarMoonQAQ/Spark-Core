package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

class EndChildrenNode(
    val level: Int = 0
): BehaviorNode() {

    override fun onTick(skill: SkillInstance): NodeStatus {
        var p = parent
        repeat(level) {
            p = p?.parent
        }
        p?.end(skill)
        return NodeStatus.SUCCESS
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return EndChildrenNode(level)
    }

    companion object {
        val CODEC: MapCodec<EndChildrenNode> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("level", 0).forGetter { it.level }
            ).apply(it, ::EndChildrenNode)
        }
    }

}