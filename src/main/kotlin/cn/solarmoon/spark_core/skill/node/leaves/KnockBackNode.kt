package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.entity.knockBackRelative
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

class KnockBackNode(
    val strength: Double = 1.0,
    val fly: Boolean = true
): BehaviorNode() {

    override fun onStart(skill: SkillInstance) {
        val entity = skill.holder as? LivingEntity ?: return
        val attacker = require<Entity>("attacker")
        if (!fly) entity.setOnGround(false)
        entity.knockBackRelative(attacker.position(), strength)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return NodeStatus.SUCCESS
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return KnockBackNode(strength, fly)
    }

    companion object {
        val CODEC: MapCodec<KnockBackNode> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.DOUBLE.optionalFieldOf("strength", 1.0).forGetter { it.strength },
                Codec.BOOL.optionalFieldOf("fly", true).forGetter { it.fly }
            ).apply(it, ::KnockBackNode)
        }
    }

}