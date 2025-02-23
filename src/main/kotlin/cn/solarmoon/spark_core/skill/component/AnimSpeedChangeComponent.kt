package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.skill.Skill
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor

class AnimSpeedChangeComponent(
    val time: Int = 7,
    val speed: Double = 0.05,
): SkillComponent() {

    override fun onAttach() {
        val level = skill.level
        val animatable = skill.holder as? IAnimatable<*> ?: return
        if (!level.isClientSide && time > 0) {
            animatable.animController.changeSpeed(time, speed)
            PacketDistributor.sendToAllPlayers(AnimSpeedChangePayload(animatable, time, speed))
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<AnimSpeedChangeComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("time", 7).forGetter { it.time },
                Codec.DOUBLE.optionalFieldOf("speed", 0.05).forGetter { it.speed }
            ).apply(it, ::AnimSpeedChangeComponent)
        }
    }

}
