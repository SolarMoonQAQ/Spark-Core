package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.registry.common.SparkSkillContext
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent

class PreventLocalInputComponent(
    val forwardImpulse: Float = 0f,
    val leftImpulse: Float = 0f,
    val up: Boolean = false,
    val down: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jumping: Boolean = false,
    val shiftKeyDown: Boolean = false,
    val sprintTriggerTime: Int = -1,
    val swinging: Boolean = false,
    val activeTime: List<Vec2> = listOf(),
): SkillComponent() {

    override fun onEvent(event: Event) {
        if (event is MovementInputUpdateEvent) {
            val time = skill.blackBoard.require(SparkSkillContext.TIME, this)
            val player = event.entity
            val input = event.input
            if (activeTime.isEmpty() || activeTime.any { time in it.x..it.y }) {
                input.forwardImpulse = forwardImpulse
                input.leftImpulse = leftImpulse
                input.up = up
                input.down = down
                input.left = left
                input.right = right
                input.jumping = jumping
                input.shiftKeyDown = shiftKeyDown
                (player as? LocalPlayer)?.sprintTriggerTime = sprintTriggerTime
                player.swinging = swinging
            }
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PreventLocalInputComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.FLOAT.optionalFieldOf("forward_impulse", 0f).forGetter { it.forwardImpulse },
                Codec.FLOAT.optionalFieldOf("left_impulse", 0f).forGetter { it.leftImpulse },
                Codec.BOOL.optionalFieldOf("up", false).forGetter { it.up },
                Codec.BOOL.optionalFieldOf("down", false).forGetter { it.down },
                Codec.BOOL.optionalFieldOf("left", false).forGetter { it.left },
                Codec.BOOL.optionalFieldOf("right", false).forGetter { it.right },
                Codec.BOOL.optionalFieldOf("jumping", false).forGetter { it.jumping },
                Codec.BOOL.optionalFieldOf("shift_key_down", false).forGetter { it.shiftKeyDown },
                Codec.INT.optionalFieldOf("sprint_trigger_time", -1).forGetter { it.sprintTriggerTime },
                Codec.BOOL.optionalFieldOf("swinging", false).forGetter { it.swinging },
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventLocalInputComponent)
        }
    }

}