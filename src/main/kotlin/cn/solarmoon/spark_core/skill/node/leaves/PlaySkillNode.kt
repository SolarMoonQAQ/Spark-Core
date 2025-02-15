package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.SkillType
import cn.solarmoon.spark_core.skill.getSkillType
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

class PlaySkillNode(
    val skillTypeKey: ResourceLocation
): BehaviorNode() {

    override fun onStart(skill: SkillInstance) {
        if (!skill.level.isClientSide) {
            getSkillType(skill.level, skillTypeKey).createSkill(skill.holder, skill.level, true)
        }
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return NodeStatus.SUCCESS
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return PlaySkillNode(skillTypeKey)
    }

    companion object {
        val CODEC: MapCodec<PlaySkillNode> = RecordCodecBuilder.mapCodec {
            it.group(
                ResourceLocation.CODEC.fieldOf("skill").forGetter { it.skillTypeKey }
            ).apply(it, ::PlaySkillNode)
        }
    }

}