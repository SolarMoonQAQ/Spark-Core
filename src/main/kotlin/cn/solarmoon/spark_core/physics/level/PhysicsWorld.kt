package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.NeedsCollisionEvent
import cn.solarmoon.spark_core.event.PhysicsContactEvent
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import com.jme3.bullet.CollisionConfiguration
import com.jme3.bullet.PhysicsSoftSpace
import com.jme3.bullet.SolverMode
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.math.Vector3f
import net.neoforged.neoforge.common.NeoForge

//TODO:将计算线程数量改为通过配置文件设置
class PhysicsWorld(val level: PhysicsLevel, numSolvers: Int = 1): PhysicsSoftSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -10_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 10_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT, CollisionConfiguration(8192,0), numSolvers
) {

    init {
        setGravity(Vector3f(0f, -9.81f, 0f))
        PhysicsBody.setDeactivationDeadline(3f)
        addTickListener(level)
        isForceUpdateAllAabbs = false
        this.solverInfo.setMode(
            SolverMode.SIMD
            or SolverMode.WarmStart
            or SolverMode.CacheFriendly
            or SolverMode.CacheDirection
            or SolverMode.ArticulatedWarmStart
            or SolverMode.Interleave
        )
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
        val o1Point = ManifoldPoint(manifoldPointId, 0)
        val o2Point = ManifoldPoint(manifoldPointId, 1)
        if (pcoA.isCollisionGroupContains(pcoB)) {
            pcoA.isColliding = true
            pcoA.collisionListeners.forEach { it.onProcessed(pcoA, pcoB, o1Point, o2Point, manifoldPointId) }
        }
        if (pcoB.isCollisionGroupContains(pcoA)) {
            pcoB.isColliding = true
            pcoB.collisionListeners.forEach { it.onProcessed(pcoB, pcoA, o2Point, o1Point, manifoldPointId) }
        }

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(pcoA, pcoB, o1Point, o2Point, manifoldPointId))
    }

    override fun addCollisionObject(pco: PhysicsCollisionObject) {
        pco.level = level
        super.addCollisionObject(pco)
    }

}