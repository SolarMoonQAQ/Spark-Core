package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

abstract class EmptyNode: BehaviorNode() {

    object Success: EmptyNode() {
        val CODEC: MapCodec<EmptyNode> = RecordCodecBuilder.mapCodec { it.stable(Success) }

        override val codec: MapCodec<out BehaviorNode> = CODEC

        override fun copy(): BehaviorNode {
            return Success
        }

        override fun onTick(skill: SkillInstance): NodeStatus {
            return NodeStatus.SUCCESS
        }
    }

    object Running: EmptyNode() {
        val CODEC: MapCodec<EmptyNode> = RecordCodecBuilder.mapCodec { it.stable(Running) }

        override val codec: MapCodec<out BehaviorNode> = CODEC

        override fun copy(): BehaviorNode {
            return Running
        }

        override fun onTick(skill: SkillInstance): NodeStatus {
            return NodeStatus.RUNNING
        }
    }

    object Failure: EmptyNode() {
        val CODEC: MapCodec<EmptyNode> = RecordCodecBuilder.mapCodec { it.stable(Failure) }

        override val codec: MapCodec<out BehaviorNode> = CODEC

        override fun copy(): BehaviorNode {
            return Failure
        }

        override fun onTick(skill: SkillInstance): NodeStatus {
            return NodeStatus.FAILURE
        }
    }

}