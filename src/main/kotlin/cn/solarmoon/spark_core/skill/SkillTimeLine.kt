package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.phys.Vec2

class SkillTimeLine(
    private val skill: Skill
) {

    var runTime = 0

    val animTime: Double? get() {
        val animatable = skill.holder as? IAnimatable<*> ?: return null
        val anim = animatable.animController.getPlayingAnim() ?: return null
        return anim.time
    }

    val normalizedTime get() = animTime ?: runTime.toDouble()

    fun match(stamp: Stamp): Boolean {
        return when(stamp.type) {
            Type.RUN -> runTime.toDouble() in stamp.x..stamp.y
            Type.ANIMATION -> animTime?.let { it in stamp.x..stamp.y } == true
        }
    }

    fun match(stamps: Collection<Stamp>): Boolean {
        return stamps.isEmpty() || stamps.any { match(it) }
    }

    data class Stamp(
        val x: Double = 0.0,
        val y: Double = 0.0,
        val type: Type = Type.ANIMATION
    ) {
        companion object {
            val CODEC: Codec<Stamp> = RecordCodecBuilder.create {
                it.group(
                    SerializeHelper.VEC2_CODEC.fieldOf("stamp").forGetter { Vec2(it.x.toFloat(), it.y.toFloat()) },
                    Codec.STRING.xmap({ Type.valueOf(it.uppercase()) }, { it.toString().lowercase() }).optionalFieldOf("type", Type.ANIMATION).forGetter { it.type }
                ).apply(it) { t, type -> Stamp(t.x.toDouble(), t.y.toDouble(), type) }
            }
        }
    }

    enum class Type { RUN, ANIMATION }

}