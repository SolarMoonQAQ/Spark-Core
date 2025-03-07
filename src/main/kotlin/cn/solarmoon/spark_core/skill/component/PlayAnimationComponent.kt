package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

class PlayAnimationComponent(
    var animIndex: AnimIndex,
    val transitionTime: Int,
    val onAnimStart: List<SkillComponent> = listOf(),
    val onAnimEnd: List<SkillComponent> = listOf()
): SkillComponent() {

    var playOnAttach = true
    lateinit var anim: AnimInstance

    override fun onAttach(): Boolean {
        val animatable = skill.holder as? IAnimatable<*> ?: return false

        anim = AnimInstance.create(animatable, animIndex) {
            var starts = listOf<SkillComponent>()
            onEvent<AnimEvent.SwitchIn> {
                starts = onAnimStart.map { it.copy() }
                starts.forEach { it.attach(skill) }
            }

            onEvent<AnimEvent.End> {
                starts.forEach { it.detach() }
                onAnimEnd.forEach { it.copy().attach(skill) }
            }
        }

        if (playOnAttach) animatable.animController.setAnimation(anim, transitionTime)
        return true
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PlayAnimationComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                AnimIndex.CODEC.fieldOf("anim_index").forGetter { it.animIndex },
                Codec.INT.fieldOf("transition_time").forGetter { it.transitionTime },
                SkillComponent.CODEC.listOf().optionalFieldOf("on_anim_start", listOf()).forGetter { it.onAnimStart },
                SkillComponent.CODEC.listOf().optionalFieldOf("on_anim_end", listOf()).forGetter { it.onAnimEnd }
            ).apply(it, ::PlayAnimationComponent)
        }
    }

}