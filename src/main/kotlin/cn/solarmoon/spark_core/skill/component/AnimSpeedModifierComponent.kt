package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.neoforged.neoforge.network.PacketDistributor

class AnimSpeedModifierComponent(
    val time: Int = 7,
    val speed: Double = 0.05,
): SkillComponent() {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return AnimSpeedModifierComponent(time, speed)
    }

    override fun onActive() {
        val animatable = skill.holder as? IAnimatable<*> ?: return
        if (!skill.level.isClientSide) {
            animatable.animController.changeSpeed(time, speed)
            PacketDistributor.sendToAllPlayers(AnimSpeedChangePayload(animatable, time, speed))
        }
    }

    override fun onUpdate() {}

    override fun onEnd() {}

    companion object {
        val CODEC: MapCodec<AnimSpeedModifierComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.optionalFieldOf("time", 7).forGetter { it.time },
                Codec.DOUBLE.optionalFieldOf("speed", 0.05).forGetter { it.speed }
            ).apply(it, ::AnimSpeedModifierComponent)
        }
    }

}