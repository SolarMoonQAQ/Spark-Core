package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.entity.getRelativeVector
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.Input
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2

class MoveSetComponent(
    val sets: List<Pair<Vec2, Vec3>>,
    val orientationByInput: Boolean = false,
    children: List<SkillComponent> = listOf()
): SkillComponent(children) {

    var inputCache: Input? = null

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return MoveSetComponent(sets, orientationByInput, children)
    }

    override fun onActive(): Boolean {
        inputCache = null
        return true
    }

    override fun onUpdate(): Boolean {
        val entity = skill.holder as? Entity ?: return false
        val time = query<AnimInstance>("animation")?.time ?: skill.runTime.toDouble()
        sets.forEach { pair ->
            val activeTime = pair.first
            val move = pair.second
            if (time in activeTime.x..activeTime.y) {
                if (entity is Player && orientationByInput && entity.isLocalPlayer) {
                    if (inputCache == null) inputCache = (entity as LocalPlayer).savedInput
                    val input = inputCache!!
                    val angle = atan2(input.moveVector.y, -input.moveVector.x) - PI.toFloat() / 2
                    val move = move.yRot(angle)
                    entity.deltaMovement = entity.getRelativeVector(move).add(0.0, -0.5, 0.0)
                } else {
                    entity.deltaMovement = entity.getRelativeVector(move).add(0.0, -0.5, 0.0)
                }
            } else {
                if (skill.level.isClientSide) inputCache = null
            }
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
                Codec.BOOL.optionalFieldOf("orientation_by_input", false).forGetter { it.orientationByInput },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::MoveSetComponent)
        }
    }

}