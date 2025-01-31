package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

class ServerPhysicsLevel(
    override val mcLevel: ServerLevel
): PhysicsLevel("Server PhysicsThread") {

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        super.physicsTick(space, timeStep)

        mcLevel.allEntities.forEach {
            NeoForge.EVENT_BUS.post(PhysicsTickEvent.Entity(it, this))
        }
    }

}