package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.NeedsCollisionEvent
import cn.solarmoon.spark_core.physics.body.PhysicsBodyEvent
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.util.triggerEvent
import com.jme3.bullet.CollisionConfiguration
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.SolverMode
import com.jme3.bullet.collision.PersistentManifolds
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Vector3f
import net.neoforged.neoforge.common.NeoForge

class PhysicsWorld(val level: PhysicsLevel) : PhysicsSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -1_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 15_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT, NativeLibrary.countThreads(), CollisionConfiguration(8192, 1)
) {

    val worldSnapshot: WorldSnapshot by lazy {
        WorldSnapshot(this)
    }

    init {
        setGravity(Vector3f(0f, -9.81f, 0f))
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
        return NeoForge.EVENT_BUS.post(NeedsCollisionEvent(pcoA, pcoB, r)).shouldCollide
    }

    var pointCount = 0
    var pointProcessed = 0
    var pointConceived = 0
    override fun onContactProcessed(
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject,
        manifoldPointId: Long
    ) {
        pointProcessed++
    }

    override fun onContactConceived(
        manifoldPointId: Long,
        manifoldId: Long,
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject
    ): Boolean {
        pointConceived++
        return false
    }
    override fun update(timeInterval: Float, maxSteps: Int, stepFlags: Int) {
        pointConceived = 0
        pointProcessed = 0
        pointCount = 0
        val manifolds = listManifoldIds().iterator()
        while (manifolds.hasNext()) {
            pointCount += PersistentManifolds.countPoints(manifolds.next())
        }
        if (!level.mcLevel.isClientSide && pcoList.isNotEmpty())
            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Pre      : $pointCount")
        super.update(timeInterval, maxSteps, stepFlags)
        val manifoldsAfterUpdate = listManifoldIds().iterator()
        pointCount = 0
        while (manifoldsAfterUpdate.hasNext()) {
            pointCount += PersistentManifolds.countPoints(manifoldsAfterUpdate.next())
        }
        if (!level.mcLevel.isClientSide && pcoList.isNotEmpty()) {
            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Conceived: $pointConceived")
            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Processed: $pointProcessed")
            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Post     : $pointCount")
            SparkCore.LOGGER.debug("-------------------------------------------------------")
        }
    }

    override fun addCollisionObject(pco: PhysicsCollisionObject) {
        super.addCollisionObject(pco)
        if (pco is PhysicsRigidBody)
            worldSnapshot.markAdd(pco) // 通知 snapshot
        pco.triggerEvent(PhysicsBodyEvent.AddToWorld())
    }

    override fun removeCollisionObject(pco: PhysicsCollisionObject) {
        super.removeCollisionObject(pco)
        if (pco is PhysicsRigidBody)
            worldSnapshot.markRemove(pco) // 通知 snapshot
    }

}