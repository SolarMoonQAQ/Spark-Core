package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity

class CameraShakeComponent(
    val time: Int,
    val strength: Float,
    val frequency: Float,
    val range: Double = 0.0
): SkillComponent() {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return CameraShakeComponent(time, strength, frequency, range)
    }

    override fun onActive() {
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

    override fun onUpdate() {
    }

    override fun onEnd() {
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