package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.skill.Skill
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Holder
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity

class PlaySoundComponent(
    val sounds: List<SoundEvent>,
    val volume: Float = 1f,
    val pitch: Float = 1f
): SkillComponent() {

    override fun onAttach() {
        val entity = skill.holder as? Entity ?: return
        skill.level.playSound(null, entity.onPos.above(), sounds.random(), SoundSource.PLAYERS, volume, pitch)
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PlaySoundComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SoundEvent.DIRECT_CODEC.listOf().fieldOf("sounds").forGetter { it.sounds },
                Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter { it.volume },
                Codec.FLOAT.optionalFieldOf("pitch", 1f).forGetter { it.pitch }
            ).apply(it, ::PlaySoundComponent)
        }
    }

}