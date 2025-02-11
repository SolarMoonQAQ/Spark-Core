package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.camera.CameraAdjuster
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.event.EntityTurnEvent
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import org.joml.Vector2f

class PreventYRotComponent(
    val activeTime: List<Vec2> = listOf()
): SkillComponent() {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return PreventYRotComponent(activeTime)
    }

    override fun onActive() {
        if (skill.level.isClientSide && activeTime.isEmpty()) CameraAdjuster.isActive = true
    }

    override fun onUpdate() {
        if (activeTime.isNotEmpty()) {
            val time = requireQuery<() -> Double>("time").invoke()
            CameraAdjuster.isActive = activeTime.any { time in it.x..it.y }
        }
    }

    override fun onEnd() {
        if (skill.level.isClientSide) CameraAdjuster.isActive = false
    }

    companion object {
        val CODEC: MapCodec<PreventYRotComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventYRotComponent)
        }
    }

}