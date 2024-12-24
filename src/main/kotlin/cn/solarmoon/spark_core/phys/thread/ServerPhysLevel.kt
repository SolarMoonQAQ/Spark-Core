package cn.solarmoon.spark_core.phys.thread

import cn.solarmoon.spark_core.event.PhysLevelTickEvent
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

class ServerPhysLevel(
    override val level: ServerLevel
): PhysLevel(level) {

    override fun physTick() {
        level.allEntities.forEach {
            NeoForge.EVENT_BUS.post(PhysLevelTickEvent.Entity(this, it))
        }
        super.physTick()
    }

}