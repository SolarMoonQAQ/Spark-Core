package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsContactEvent
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsSpace.BroadphaseType
import com.jme3.bullet.collision.PersistentManifolds
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Vector3f
import net.neoforged.neoforge.common.NeoForge

class PhysicsWorld(
    val physicsLevel: PhysicsLevel
): PhysicsSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -10_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 10_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT
) {
    
    init {
        setGravity(Vector3f(0f, -9.81f, 0f))
        addTickListener(physicsLevel)
    }

    override fun onContactStarted(manifoldId: Long) {
        super.onContactStarted(manifoldId)
        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Start(manifoldId))
    }

    override fun onContactProcessed(
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject,
        manifoldPointId: Long
    ) {
        super.onContactProcessed(pcoA, pcoB, manifoldPointId)
        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(manifoldPointId, pcoA, pcoB))
    }

    override fun onContactEnded(manifoldId: Long) {
        super.onContactEnded(manifoldId)
        NeoForge.EVENT_BUS.post(PhysicsContactEvent.End(manifoldId))
    }
    
}