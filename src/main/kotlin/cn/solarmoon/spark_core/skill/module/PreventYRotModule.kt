package cn.solarmoon.spark_core.skill.module

import cn.solarmoon.spark_core.camera.CameraAdjuster
import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2

data class PreventYRotModule(
    val activeTime: List<Vec2> = listOf()
) {

    fun active(level: Level) {
        if (level.isClientSide && activeTime.isEmpty()) CameraAdjuster.isActive = true
    }

    fun update(time: Double) {
        if (activeTime.isNotEmpty()) {
            CameraAdjuster.isActive = activeTime.any { time in it.x..it.y }
        }
    }

    fun end(level: Level) {
        if (level.isClientSide) CameraAdjuster.isActive = false
    }

    companion object {
        val CODEC: Codec<PreventYRotModule> = RecordCodecBuilder.create {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time", listOf()).forGetter { it.activeTime }
            ).apply(it, ::PreventYRotModule)
        }
    }

}