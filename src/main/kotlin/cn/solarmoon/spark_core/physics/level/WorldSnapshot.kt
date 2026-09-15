package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.physics.body.CollisionGroups
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsCollisionObject
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
        if (pco.collisionGroup == CollisionGroups.PHYSICS_BODY) {
            trackedBodies.add(pco)
            structureDirty = true
        }
    }

    /**
     * 标记需要移除一个刚体
     */
    fun markRemove(pco: PhysicsRigidBody) {
        trackedBodies.remove(pco)
        structureDirty = true
    }

    /**
     * 收集包围盒与给定查询盒相交的快照刚体（主线程）。
     *
     * 等价于 `contactTest` 的宽相候选集（AABB 相交），但**不跑窄相**，
     * 供爆炸等主线程订阅者做粗筛：先用纯 Java 算术排除够不到任何刚体的射线，
     * 只有通过粗筛的射线才进入 `rayTest`。
     *
     * @param min    查询盒最小角（物理空间坐标，闭区间）
     * @param max    查询盒最大角（物理空间坐标，闭区间）
     * @param out    结果列表，调用时会被清空并填充相交的快照刚体
     */
    fun collectBodiesInAabb(min: Vector3f, max: Vector3f, out: MutableList<PhysicsRigidBody>) {
        out.clear()
        val tmpMin = Vector3f()
        val tmpMax = Vector3f()
        for (snap in snapshotMap.values) {
            val bb = snap.boundingBox(null)
            bb.getMin(tmpMin)
            bb.getMax(tmpMax)
            if (tmpMax.x >= min.x && tmpMin.x <= max.x &&
                tmpMax.y >= min.y && tmpMin.y <= max.y &&
                tmpMax.z >= min.z && tmpMin.z <= max.z
            ) {
                out.add(snap)
            }
        }
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
            if (!main.isStatic)
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
        snap.isKinematic = true
        // 直接写 userObject，用于查找owner
        snap.userObject = main.userObject
        // 同步碰撞组
        snap.collisionGroup = main.collisionGroup
        snap.collideWithGroups = main.collideWithGroups

        // 初始 transform
        snap.setPhysicsTransform(main.getTransform(null))
        return snap
    }

    override fun needsCollision(pcoA: PhysicsCollisionObject?, pcoB: PhysicsCollisionObject?): Boolean {
        return false // 不需要碰撞处理
    }
}