package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.camera.setCameraLock
import cn.solarmoon.spark_core.data.SerializeHelper
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class PreventYRotComponent(
    val activeTime: List<Vec2>? = listOf()
) {

    fun update(entity: Entity, time: Double) {
        if (activeTime == null) return
        entity.setCameraLock(activeTime.any { time in it.x..it.y } || activeTime.isEmpty())
    }

    companion object {
        val CODEC: Codec<PreventYRotComponent> = RecordCodecBuilder.create {
            it.group(
                SerializeHelper.VEC2_CODEC.listOf().optionalFieldOf("active_time").forGetter { Optional.ofNullable(it.activeTime) }
            ).apply(it) { PreventYRotComponent(it.getOrNull()) }
        }
    }

}