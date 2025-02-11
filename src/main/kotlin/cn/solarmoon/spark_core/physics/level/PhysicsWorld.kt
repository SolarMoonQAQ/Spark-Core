package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
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

    override fun needsCollision(pcoA: PhysicsCollisionObject?, pcoB: PhysicsCollisionObject?): Boolean {
        if (pcoA != null && pcoB != null && pcoA.owner == pcoB.owner && !pcoA.collideWithOwner && !pcoB.collideWithOwner) return false
        return super.needsCollision(pcoA, pcoB)
    }

    override fun onContactStarted(manifoldId: Long) {
        super.onContactStarted(manifoldId)

        val a = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyAId(manifoldId))
        val b = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))

        a.collisionListeners.forEach { if (a.isCollisionGroupContains(b)) it.onStarted(a, b, manifoldId) }
        b.collisionListeners.forEach { if (b.isCollisionGroupContains(a)) it.onStarted(b, a, manifoldId) }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Start(manifoldId))
    }

    override fun onContactProcessed(
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject,
        manifoldPointId: Long
    ) {
        super.onContactProcessed(pcoA, pcoB, manifoldPointId)

        if (pcoA.isCollisionGroupContains(pcoB)) pcoB.isColliding = true
        if (pcoB.isCollisionGroupContains(pcoA)) pcoA.isColliding = true

        pcoA.collisionListeners.forEach { if (pcoA.isCollisionGroupContains(pcoB)) it.onProcessed(pcoA, pcoB, manifoldPointId) }
        pcoB.collisionListeners.forEach { if (pcoB.isCollisionGroupContains(pcoA)) it.onProcessed(pcoB, pcoA, manifoldPointId) }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(manifoldPointId, pcoA, pcoB))
    }

    override fun onContactEnded(manifoldId: Long) {
        super.onContactEnded(manifoldId)

        val a = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyAId(manifoldId))
        val b = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))
        a.collisionListeners.forEach { if (a.isCollisionGroupContains(b)) it.onEnded(a, b, manifoldId) }
        b.collisionListeners.forEach { if (b.isCollisionGroupContains(a)) it.onEnded(b, a, manifoldId) }

        a.isColliding = false
        b.isColliding = false

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.End(manifoldId))
    }
    
}