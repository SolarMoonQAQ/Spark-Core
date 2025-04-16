package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge

class ServerPhysicsLevel(
    override val mcLevel: ServerLevel
): PhysicsLevel("Server PhysicsThread", mcLevel) {

    override fun prePhysicsTick(space: PhysicsSpace, timeStep: Float) {
        super.prePhysicsTick(space, timeStep)

        mcLevel.allEntities.forEach {
            NeoForge.EVENT_BUS.post(PhysicsEntityTickEvent(it))
        }
    }

}