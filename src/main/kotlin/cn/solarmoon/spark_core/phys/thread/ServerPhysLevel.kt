package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.PhysTickEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

class ServerPhysLevel(
    id: ResourceLocation,
    name: String,
    override val level: ServerLevel,
    tickStep: Long,
    customApply: Boolean
): PhysLevel(id, name, level, tickStep, customApply) {

    override fun physTick() {
        super.physTick()
        NeoForge.EVENT_BUS.post(PhysTickEvent.Level(this))

        level.allEntities.forEach { NeoForge.EVENT_BUS.post(PhysTickEvent.Entity(it, this)) }
    }

}