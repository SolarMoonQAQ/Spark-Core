package cn.solarmoon.spark_core.physics.level

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import java.util.concurrent.ConcurrentHashMap

/**
 * 只读物理快照世界。
 *
 * 用于主线程进行 rayTest / sweepTest / contactTest。
 *
 * 特性：
 * - 不参与物理积分
 * - 不触发任何 PhysicsBodyEvent
 * - 不触发 owner setter
 * - 不 clone shape
 * - 结构稳定，增量同步
 */
class WorldSnapshot(
    world: PhysicsWorld
) : PhysicsSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -1_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 15_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT, 1
) {

    /**
     * 主世界 PCO -> 快照 PCO 映射
     */
    private val snapshotMap =
        ConcurrentHashMap<PhysicsRigidBody, PhysicsRigidBody>()

    /**
     * 当前已注册的主世界刚体集合
     */
    private val trackedBodies =
        HashSet<PhysicsRigidBody>()

    /**
     * 标记结构是否变更
     */
    @Volatile
    var structureDirty = false
        private set

    /**
     * 标记需要新增一个刚体
     */
    fun markAdd(pco: PhysicsRigidBody) {
        trackedBodies.add(pco)
        structureDirty = true
    }

    /**
     * 标记需要移除一个刚体
     */
    fun markRemove(pco: PhysicsRigidBody) {
        trackedBodies.remove(pco)
        structureDirty = true
    }

    /**
     * 同步结构（只在 dirty 时执行）
     *
     * 必须在物理线程停止期间调用
     */
    fun syncStructure() {
        if (!structureDirty) return

        // 1️⃣ 删除已经不存在的
        val iterator = snapshotMap.entries.iterator()
        while (iterator.hasNext()) {
            val (main, snap) = iterator.next()
            if (!trackedBodies.contains(main)) {
                removeCollisionObject(snap)
                iterator.remove()
            }
        }

        // 2️⃣ 添加新增的
        for (main in trackedBodies) {
            if (!snapshotMap.containsKey(main)) {
                val snap = createSnapshotObject(main)
                snapshotMap[main] = snap
                addCollisionObject(snap)
            }
        }

        structureDirty = false
    }

    /**
     * 同步 transform（每 tick 调用）
     */
    fun syncTransform() {
        for ((main, snap) in snapshotMap) {
            if (!main.isStatic && (main.isActive || main.isKinematic))
                snap.setPhysicsTransform(main.getTransform(null))
        }
    }

    /**
     * 创建快照刚体
     *
     * ⚠ 不调用 owner setter
     * ⚠ 不触发事件
     * ⚠ 共享 shape
     */
    private fun createSnapshotObject(main: PhysicsRigidBody): PhysicsRigidBody {
        // 共享 shape（不 clone）
        val snap = PhysicsRigidBody(main.collisionShape)

        // 直接写 userObject，用于查找owner
        snap.userObject = main.userObject

        // 同步碰撞组
        snap.collisionGroup = main.collisionGroup
        snap.collideWithGroups = main.collideWithGroups

        // 初始 transform
        snap.setPhysicsTransform(main.getTransform(null))

        return snap
    }
}