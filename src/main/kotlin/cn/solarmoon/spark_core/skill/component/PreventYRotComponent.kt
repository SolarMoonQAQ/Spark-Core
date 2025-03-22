package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.skill.SkillTimeLine
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity

class PreventYRotComponent(
    val activeTime: List<SkillTimeLine.Stamp> = listOf()
): SkillComponent() {

    override fun onTick() {
        val entity = skill.holder as? Entity ?: return
        entity.setCameraLock(skill.timeline.match(activeTime))
    }

    override fun onDetach() {
        val entity = skill.holder as? Entity ?: return
        entity.setCameraLock(false)
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PreventYRotComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SkillTimeLine.Stamp.CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime },
            ).apply(it, ::PreventYRotComponent)
        }
    }

}