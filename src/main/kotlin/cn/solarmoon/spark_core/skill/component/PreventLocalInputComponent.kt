package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.skill.SkillTimeLine
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
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
    val activeTime: List<SkillTimeLine.Stamp> = listOf(),
): SkillComponent() {

    override fun onEvent(event: Event) {
        if (event is MovementInputUpdateEvent) {
            val player = event.entity
            val input = event.input
            if (skill.timeline.match(activeTime)) {
                input.forwardImpulse *= forwardImpulse
                input.leftImpulse *= leftImpulse
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

    override fun onTick() {
        val entity = skill.holder as? LivingEntity ?: return
        if (entity is Player) return

        val movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED)
        if (skill.timeline.match(activeTime)) {
            movementSpeed?.addOrReplacePermanentModifier(SKILL_SPEED_MODIFIER)
        } else {
            movementSpeed?.removeModifier(SKILL_SPEED_MODIFIER)
        }
    }

    override fun onDetach() {
        val entity = skill.holder as? LivingEntity ?: return
        val movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED)
        movementSpeed?.removeModifier(SKILL_SPEED_MODIFIER)
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
                SkillTimeLine.Stamp.CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime },
            ).apply(it, ::PreventLocalInputComponent)
        }

        val SKILL_SPEED_ID = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "stop_move")
        val SKILL_SPEED_MODIFIER = AttributeModifier(SKILL_SPEED_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
    }

}