package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.NeedsCollisionEvent
import cn.solarmoon.spark_core.event.PhysicsContactEvent
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

    override fun needsCollision(pcoA: PhysicsCollisionObject, pcoB: PhysicsCollisionObject): Boolean {
        var r = true
        if (pcoA.owner == pcoB.owner && !pcoA.collideWithOwner && !pcoB.collideWithOwner) r = false
        return NeoForge.EVENT_BUS.post(NeedsCollisionEvent(pcoA, pcoB, r)).shouldCollide
    }

    override fun onContactStarted(manifoldId: Long) {
        val pcoA = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyAId(manifoldId))
        val pcoB = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))

        if (pcoA.isCollisionGroupContains(pcoB)) pcoA.collisionListeners.forEach { it.onStarted(pcoA, pcoB, manifoldId) }
        if (pcoB.isCollisionGroupContains(pcoA)) pcoB.collisionListeners.forEach { it.onStarted(pcoB, pcoA, manifoldId) }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Start(manifoldId))
    }

    override fun onContactProcessed(
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject,
        manifoldPointId: Long
    ) {
        if (pcoA.isCollisionGroupContains(pcoB)) {
            pcoA.isColliding = true
            pcoA.collisionListeners.forEach { it.onProcessed(pcoA, pcoB, manifoldPointId) }
        }
        if (pcoB.isCollisionGroupContains(pcoA)) {
            pcoB.isColliding = true
            pcoB.collisionListeners.forEach { it.onProcessed(pcoB, pcoA, manifoldPointId) }
        }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(manifoldPointId, pcoA, pcoB))
    }

    override fun onContactEnded(manifoldId: Long) {
        val pcoA = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyAId(manifoldId))
        val pcoB = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))

        if (pcoA.isCollisionGroupContains(pcoB)) pcoA.collisionListeners.forEach { it.onEnded(pcoA, pcoB, manifoldId) }
        if (pcoB.isCollisionGroupContains(pcoA)) pcoB.collisionListeners.forEach { it.onEnded(pcoB, pcoA, manifoldId) }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.End(manifoldId))
    }
    
}