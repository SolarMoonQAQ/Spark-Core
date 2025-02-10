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
    val timeType: String = "skill",
    val activeTime: List<Vec2> = listOf(),
    children: List<SkillComponent> = listOf()
): SkillComponent(children) {

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return PreventYRotComponent(timeType, activeTime, children)
    }

    override fun onActive(): Boolean {
        if (skill.level.isClientSide && activeTime.isEmpty()) CameraAdjuster.isActive = true
        return true
    }

    override fun onUpdate(): Boolean {
        if (activeTime.isNotEmpty()) {
            val time = query<AnimInstance>("animation")?.time ?: skill.runTime.toDouble()
            CameraAdjuster.isActive = activeTime.any { time in it.x..it.y }
        }
        return true
    }

    override fun onEnd(): Boolean {
        if (skill.level.isClientSide) CameraAdjuster.isActive = false
        return true
    }

    companion object {
        val CODEC: MapCodec<PreventYRotComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.optionalFieldOf("time_type", "skill").forGetter { it.timeType },
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::PreventYRotComponent)
        }
    }

}