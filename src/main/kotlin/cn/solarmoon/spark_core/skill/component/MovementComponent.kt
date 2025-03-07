package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.getRelativeVector
import cn.solarmoon.spark_core.molang.core.value.DoubleValue
import cn.solarmoon.spark_core.molang.core.value.Vector3k
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator
import cn.solarmoon.spark_core.skill.SkillTimeLine
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.Input
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import kotlin.math.PI
import kotlin.math.atan2

open class MovementComponent(
    val sets: List<Pair<SkillTimeLine.Stamp, Vector3k>> = listOf(),
    val orientationByInput: Boolean = false,
): SkillComponent() {
    constructor(sets: Collection<Pair<SkillTimeLine.Stamp, Vec3>> = listOf(), orientationByInput: Boolean = false):
            this(sets.map { Pair(it.first, Vector3k(DoubleValue(it.second.x), DoubleValue(it.second.y), DoubleValue(it.second.z))) }, orientationByInput)

    var inputCache: Input? = null

    override fun onAttach(): Boolean {
        inputCache = null
        return true
    }

    override fun onTick() {
        val entity = skill.holder as? Entity ?: return
        val level = skill.level
        sets.forEach { pair ->
            val activeTime = pair.first
            val move = pair.second.eval(ExpressionEvaluator.evaluator(entity)).toVec3()
            if (skill.timeline.match(activeTime)) {
                if (entity is Player && orientationByInput && entity.isLocalPlayer) {
                    if (inputCache == null) inputCache = (entity as LocalPlayer).savedInput
                    val input = inputCache!!
                    val angle = atan2(input.moveVector.y, -input.moveVector.x) - PI.toFloat() / 2
                    val move = move.yRot(angle)
                    entity.deltaMovement = entity.getRelativeVector(move)
                } else {
                    entity.deltaMovement = entity.getRelativeVector(move)
                }
            } else {
                if (level.isClientSide) inputCache = null
            }
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<MovementComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.pair(SkillTimeLine.Stamp.CODEC.fieldOf("active_time").codec(), Vector3k.CODEC.fieldOf("move").codec()).listOf().fieldOf("sets").forGetter { it.sets },
                Codec.BOOL.optionalFieldOf("orientation_by_input", false).forGetter { it.orientationByInput }
            ).apply(it, ::MovementComponent)
        }
    }

}