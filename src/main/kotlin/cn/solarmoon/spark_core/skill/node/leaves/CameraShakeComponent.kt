package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity

class CameraShakeComponent(
    val time: Int,
    val strength: Float,
    val frequency: Float,
    val range: Double = 0.0
): BehaviorNode() {

    override fun onStart(skill: SkillInstance) {
        val entity = skill.holder as? Entity ?: return
        if (!skill.level.isClientSide) {
            SparkVisualEffects.CAMERA_SHAKE.shakeToClient(entity, time, strength, frequency)
            if (range > 0) {
                skill.level.getEntities(entity, entity.boundingBox.inflate(range)).forEach {
                    SparkVisualEffects.CAMERA_SHAKE.shakeToClient(it, time, strength, frequency)
                }
            }
        }
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return NodeStatus.SUCCESS
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return CameraShakeComponent(time, strength, frequency, range)
    }

    companion object {
        val CODEC: MapCodec<CameraShakeComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.fieldOf("time").forGetter { it.time },
                Codec.FLOAT.fieldOf("strength").forGetter { it.strength },
                Codec.FLOAT.fieldOf("frequency").forGetter { it.frequency },
                Codec.DOUBLE.optionalFieldOf("range", 0.0).forGetter { it.range }
            ).apply(it, ::CameraShakeComponent)
        }
    }

}