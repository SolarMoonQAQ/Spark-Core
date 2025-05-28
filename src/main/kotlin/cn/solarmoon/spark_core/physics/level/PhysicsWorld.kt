package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.NeedsCollisionEvent
import cn.solarmoon.spark_core.event.PhysicsContactEvent
import com.jme3.bullet.CollisionConfiguration
import com.jme3.bullet.PhysicsSoftSpace
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Vector3f
import net.neoforged.neoforge.common.NeoForge
//TODO:将计算线程数量改为通过配置文件设置
class PhysicsWorld(val level: PhysicsLevel): PhysicsSoftSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -10_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 10_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT, CollisionConfiguration(8192,0), 12
) {

    init {
        setGravity(Vector3f(0f, -9.81f, 0f))
        PhysicsBody.setDeactivationDeadline(3f)
        addTickListener(level)
        isForceUpdateAllAabbs = false
    }

    /**
     * @return 在碰撞预检测时是否能够继续执行后续真实碰撞及检测
     */
    override fun needsCollision(pcoA: PhysicsCollisionObject, pcoB: PhysicsCollisionObject): Boolean {
        if (pcoA.isStatic && pcoB.isStatic) return false
        var r = true
        if ((pcoA.owner == pcoB.owner && !pcoA.collideWithOwner && !pcoB.collideWithOwner)) r = false
        return NeoForge.EVENT_BUS.post(NeedsCollisionEvent(pcoA, pcoB, r)).shouldCollide
    }

    /**
     * 处理接触点
     */
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
            pcoB.collisionListeners.forEach { it.onProcessed(pcoA, pcoB, manifoldPointId) }
        }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(manifoldPointId, pcoA, pcoB))
    }

    override fun addCollisionObject(pco: PhysicsCollisionObject) {
        pco.level = level
        super.addCollisionObject(pco)
    }

}