package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.NeedsCollisionEvent
import cn.solarmoon.spark_core.event.PhysicsContactEvent
import cn.solarmoon.spark_core.physics.body.ManifoldPoint
import cn.solarmoon.spark_core.physics.body.PhysicsBodyEvent
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.util.triggerEvent
import com.jme3.bullet.CollisionConfiguration
import com.jme3.bullet.PhysicsSoftSpace
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.SolverMode
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Vector3f
import net.minecraft.world.entity.LivingEntity
import net.neoforged.neoforge.common.NeoForge

//TODO:将计算线程数量改为通过配置文件设置
class PhysicsWorld(val level: PhysicsLevel) : PhysicsSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -1_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 15_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT, NativeLibrary.countThreads(), CollisionConfiguration(8192, 1)
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
        if ((pcoA.owner != null && pcoB.owner != null && pcoA.owner == pcoB.owner && pcoA.collideWithOwnerGroups and pcoB.collideWithOwnerGroups == 0)) r =
            false
        if ((pcoA.owner != null && pcoB.owner != null && pcoA.owner is LivingEntity && pcoB.owner is LivingEntity)) r =
            false
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

        pcoA.isColliding = true
        pcoA.triggerEvent(PhysicsBodyEvent.Collide.Processed(pcoA, pcoB, o1Point, o2Point))

        pcoB.isColliding = true
        pcoB.triggerEvent(PhysicsBodyEvent.Collide.Processed(pcoB, pcoA, o2Point, o1Point))

        NeoForge.EVENT_BUS.post(PhysicsContactEvent.Process(pcoA, pcoB, o1Point, o2Point, manifoldPointId))
    }

    override fun addCollisionObject(pco: PhysicsCollisionObject) {
        super.addCollisionObject(pco)
        pco.triggerEvent(PhysicsBodyEvent.AddToWorld())
    }

}