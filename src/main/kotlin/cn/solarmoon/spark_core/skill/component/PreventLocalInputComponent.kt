package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.flag.SparkFlags
import cn.solarmoon.spark_core.flag.getFlag
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.common.NeoForge

class PreventLocalInputComponent(
    val activeTime: List<Vec2> = listOf(),
): SkillComponent() {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return PreventLocalInputComponent(activeTime)
    }

    @SubscribeEvent
    private fun playerMove(event: MovementInputUpdateEvent) {
        val player = event.entity
        val input = event.input
        val time = requireQuery<() -> Double>("time").invoke()
        if (activeTime.isEmpty() || activeTime.any { time in it.x..it.y }) {
            input.forwardImpulse = 0f
            input.leftImpulse = 0f
            input.up = false
            input.down = false
            input.left = false
            input.right = false
            input.jumping = false
            input.shiftKeyDown = false
            (player as? LocalPlayer)?.sprintTriggerTime = -1
            player.swinging = false
        }
    }

    override fun onActive() {
        if (skill.level.isClientSide) {
            NeoForge.EVENT_BUS.register(this)
        }
    }

    override fun onUpdate() {}

    override fun onEnd() {
        if (skill.level.isClientSide) {
            NeoForge.EVENT_BUS.unregister(this)
        }
    }

    companion object {
        val CODEC: MapCodec<PreventLocalInputComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventLocalInputComponent)
        }
    }

}