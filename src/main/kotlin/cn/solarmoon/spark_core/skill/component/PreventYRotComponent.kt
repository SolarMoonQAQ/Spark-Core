package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.registry.common.SparkSkillContext
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import kotlin.ranges.contains

class PreventYRotComponent(
    val activeTime: List<Vec2> = listOf()
): SkillComponent() {

    override fun onTick() {
        val entity = skill.holder as? Entity ?: return
        val time = skill.blackBoard.require(SparkSkillContext.TIME, this)
        entity.setCameraLock(activeTime.any { time in it.x..it.y } || activeTime.isEmpty())
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PreventYRotComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventYRotComponent)
        }
    }

}