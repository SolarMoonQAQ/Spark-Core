package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.entity.getRelativeVector
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class MoveSetComponent(
    val sets: List<Pair<Vec2, Vec3>>,
    val timeType: String = "skill",
    children: List<SkillComponent> = listOf()
): SkillComponent(children) {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return MoveSetComponent(sets, timeType, children)
    }

    override fun onActive(): Boolean {
        return true
    }

    override fun onUpdate(): Boolean {
        val entity = skill.holder as? Entity ?: return false
        val time = if (timeType == "anim") registerContext(AnimInstance::class).time else skill.runTime.toDouble()
        sets.forEach { pair ->
            val activeTime = pair.first
            val move = pair.second
            if (time in activeTime.x..activeTime.y) entity.deltaMovement = entity.getRelativeVector(move).add(0.0, -0.5, 0.0)
        }
        return true
    }

    override fun onEnd(): Boolean {
        return true
    }

    companion object {
        val CODEC: MapCodec<MoveSetComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.pair(SerializeHelper.VEC2_CODEC.fieldOf("active_time").codec(), Vec3.CODEC.fieldOf("move").codec()).listOf().fieldOf("sets").forGetter { it.sets },
                Codec.STRING.optionalFieldOf("time_type", "skill").forGetter { it.timeType },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::MoveSetComponent)
        }
    }

}