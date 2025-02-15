package cn.solarmoon.spark_core.skill.node.leaves

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

class PlayAnimationNode(
    val animResource: AnimIndex,
    val transitionTime: Int,
    val shouldTurnBody: Boolean,
    val onAnimTick: BehaviorNode = EmptyNode.Running,
    val onAnimEnd: BehaviorNode = EmptyNode.Success
): BehaviorNode() {

    private var anim: AnimInstance? = null

    private var status = NodeStatus.RUNNING

    init {
        dynamicContainer.addChild(onAnimTick)
        dynamicContainer.addChild(onAnimEnd)
    }

    override fun onStart(skill: SkillInstance) {
        val animatable = skill.holder as? IEntityAnimatable<*> ?: return
        val name = animResource.name
        anim = AnimInstance.create(animatable, name, OAnimationSet.get(animResource.index).getAnimation(animResource.name)!!) {
            shouldTurnBody = this@PlayAnimationNode.shouldTurnBody

            onTick {
                status = onAnimTick.tick(skill)
            }

            onEnd {
                skill.level.submitImmediateTask {
                    status = onAnimEnd.tick(skill)
                }
            }
        }
        write("animation", anim!!)
        write("time", { anim!!.time })
        animatable.animController.setAnimation(anim!!, transitionTime)
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        return status
    }

    override fun onEnd(skill: SkillInstance) {
        anim?.cancel()
    }

    override val codec: MapCodec<out BehaviorNode> = CODEC

    override fun copy(): BehaviorNode {
        return PlayAnimationNode(animResource, transitionTime, shouldTurnBody, onAnimTick.copy(), onAnimEnd.copy())
    }

    companion object {
        val CODEC: MapCodec<PlayAnimationNode> = RecordCodecBuilder.mapCodec {
            it.group(
                AnimIndex.CODEC.fieldOf("anim_index").forGetter { it.animResource },
                Codec.INT.optionalFieldOf("transition_time", 7).forGetter { it.transitionTime },
                Codec.BOOL.optionalFieldOf("should_turn_body", true).forGetter { it.shouldTurnBody },
                BehaviorNode.CODEC.optionalFieldOf("on_anim_tick", EmptyNode.Running).forGetter { it.onAnimTick },
                BehaviorNode.CODEC.optionalFieldOf("on_anim_end", EmptyNode.Success).forGetter { it.onAnimEnd }
            ).apply(it, ::PlayAnimationNode)
        }
    }

}