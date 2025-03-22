package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class ServerPhysicsLevel(
    override val mcLevel: ServerLevel
): PhysicsLevel("Server PhysicsThread") {

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        super.physicsTick(space, timeStep)

        mcLevel.allEntities.forEach {
            NeoForge.EVENT_BUS.post(PhysicsEntityTickEvent(it))
        }
    }
    override val taskMap: ConcurrentHashMap<String, () -> Unit>
        get() = ConcurrentHashMap()
    override val immediateQueue: ConcurrentLinkedDeque<() -> Unit>
        get() = ConcurrentLinkedDeque()
}