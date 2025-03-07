package cn.solarmoon.spark_core.physics.level

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

        handleContacts(a, b, manifoldId, false)
        handleContacts(b, a, manifoldId, false)

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

        handleContacts(pcoA, pcoB, manifoldPointId, false)
        handleContacts(pcoB, pcoA, manifoldPointId, false)

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(manifoldPointId, pcoA, pcoB))
    }

    override fun onContactEnded(manifoldId: Long) {
        super.onContactEnded(manifoldId)

        val a = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyAId(manifoldId))
        val b = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))

        handleContacts(a, b, manifoldId, true)
        handleContacts(b, a, manifoldId, true)

        a.isColliding = false
        b.isColliding = false

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.End(manifoldId))
    }

    private fun handleContacts(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long, end: Boolean) {
        if (!end) {
            val contacts = o1.allContacts.getOrPut(o2.nativeId()) { mutableSetOf() }

            // 当禁用时，删除当前接触，当删除后列表为空，则触发end
            if (!o1.isCollisionGroupContains(o2)) {
                if (contacts.remove(manifoldId) && contacts.isEmpty()) o1.collisionListeners.forEach { it.onEnded(o1, o2, manifoldId) }
                return
            }

            if (contacts.isEmpty()) o1.collisionListeners.forEach { it.onStarted(o1, o2, manifoldId) }
            contacts.add(manifoldId)
        } else {
            val contacts = o1.allContacts[o2.nativeId()] ?: return
            if (contacts.remove(manifoldId) && contacts.isEmpty()) o1.collisionListeners.forEach { it.onEnded(o1, o2, manifoldId) }
        }
    }
    
}