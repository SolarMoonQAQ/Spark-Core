package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

class PlayAnimationComponent(
    val animResource: AnimIndex,
    val transitionTime: Int,
    val shouldTurnBody: Boolean,
    children: List<SkillComponent>
): SkillComponent(children) {

    lateinit var anim: AnimInstance

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return PlayAnimationComponent(animResource, transitionTime, shouldTurnBody, children)
    }

    override fun onActive(skill: SkillInstance): Boolean {
        val animatable = skill.holder as? IEntityAnimatable<*> ?: return false
        anim = AnimInstance.create(animatable, animResource.name, OAnimationSet.get(animResource.index).getAnimation(animResource.name)!!) {
            shouldTurnBody = this@PlayAnimationComponent.shouldTurnBody

            onEnd {
                skill.end()
            }
        }
        addContext(anim)
        animatable.animController.setAnimation(anim, transitionTime)
        return true
    }

    override fun onUpdate(skill: SkillInstance): Boolean {
        return true
    }

    override fun onEnd(skill: SkillInstance): Boolean {
        anim.cancel()
        return true
    }

    companion object {
        val CODEC: MapCodec<PlayAnimationComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                AnimIndex.CODEC.fieldOf("anim_index").forGetter { it.animResource },
                Codec.INT.optionalFieldOf("transition_time", 7).forGetter { it.transitionTime },
                Codec.BOOL.optionalFieldOf("should_turn_body", true).forGetter { it.shouldTurnBody },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::PlayAnimationComponent)
        }
    }

}