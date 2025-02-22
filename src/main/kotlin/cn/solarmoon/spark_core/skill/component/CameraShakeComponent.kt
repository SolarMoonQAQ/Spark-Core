package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity

data class CameraShakeComponent(
    val time: Int,
    val strength: Float,
    val frequency: Float,
    val range: Double = 0.0
) {

    fun active(entity: Entity) {
        val level = entity.level()
        if (!level.isClientSide) {
            SparkVisualEffects.CAMERA_SHAKE.shakeToClient(entity, time, strength, frequency)
            if (range > 0) {
                level.getEntities(entity, entity.boundingBox.inflate(range)).forEach {
                    SparkVisualEffects.CAMERA_SHAKE.shakeToClient(it, time, strength, frequency)
                }
            }
        }
    }

    companion object {
        val CODEC: Codec<CameraShakeComponent> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.fieldOf("time").forGetter { it.time },
                Codec.FLOAT.fieldOf("strength").forGetter { it.strength },
                Codec.FLOAT.fieldOf("frequency").forGetter { it.frequency },
                Codec.DOUBLE.optionalFieldOf("range", 0.0).forGetter { it.range }
            ).apply(it, ::CameraShakeComponent)
        }
    }

}