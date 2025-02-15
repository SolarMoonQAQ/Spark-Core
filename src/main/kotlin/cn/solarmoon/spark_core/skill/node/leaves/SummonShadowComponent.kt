package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import java.awt.Color

class SummonShadowComponent(
    val maxLifeTime: Int = 20,
    val color: Int = Color.GRAY.rgb
): BehaviorNode() {

    override fun onStart(skill: SkillInstance) {
        val entity = skill.holder as? Entity ?: return
        if (!entity.level().isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(entity.id, maxLifeTime, Color(color))
        }
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return NodeStatus.SUCCESS
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return SummonShadowComponent(maxLifeTime, color)
    }

    companion object {
        val CODEC: MapCodec<SummonShadowComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("max_life_time", 20).forGetter { it.maxLifeTime },
                Codec.INT.optionalFieldOf("color", Color.GRAY.rgb).forGetter { it.color }
            ).apply(it, ::SummonShadowComponent)
        }
    }

}