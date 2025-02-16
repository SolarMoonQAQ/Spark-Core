package cn.solarmoon.spark_core.skill.module

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor

data class AnimSpeedChangeModule(
    val time: Int = 7,
    val speed: Double = 0.05,
) {

    fun active(animatable: IAnimatable<*>, level: Level) {
        if (!level.isClientSide && time > 0) {
            animatable.animController.changeSpeed(time, speed)
            PacketDistributor.sendToAllPlayers(AnimSpeedChangePayload(animatable, time, speed))
        }
    }

    companion object {
        val CODEC: Codec<AnimSpeedChangeModule> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.optionalFieldOf("time", 7).forGetter { it.time },
                Codec.DOUBLE.optionalFieldOf("speed", 0.05).forGetter { it.speed }
            ).apply(it, ::AnimSpeedChangeModule)
        }
    }

}
