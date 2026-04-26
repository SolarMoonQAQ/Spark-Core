package cn.solarmoon.spark_core.compat.create

import com.simibubi.create.content.contraptions.Contraption
import net.minecraft.world.phys.Vec3

/**
 * 跨线程传输的同步快照（不可变，用于 @Volatile 原子发布）
 *
 * @property position 权威位置（世界坐标）
 * @property xRot 绕X轴旋转（度，pitch）
 * @property yRot 绕Y轴旋转（度，yaw）
 * @property zRot 绕Z轴旋转（度，roll）
 * @property secondYRot 第二Y轴旋转（度，某些装置需要）
 * @property linearVelocity 线速度（方块/秒，世界坐标）
 * @property angularVelX X轴角速度（度/秒）
 * @property angularVelY Y轴角速度（度/秒）
 * @property angularVelZ Z轴角速度（度/秒）
 * @property nanoTime 快照时间戳（System.nanoTime）
 */
data class ContraptionSyncSnapshot(
    val position: Vec3,
    val xRot: Float,
    val yRot: Float,
    val zRot: Float,
    val secondYRot: Float,
    val linearVelocity: Vec3,
    val angularVelX: Float,
    val angularVelY: Float,
    val angularVelZ: Float,
    val nanoTime: Long
)

/**
 * Create 装置物理同步状态缓存。
 *
 * 作用：
 * 1) 缓存"碰撞形状是否需要重建"的脏标记；
 * 2) 缓存最近一次参与同步的装置引用与位姿；
 * 3) 提供跨线程速度外推所需的同步快照。
 */
class CreateContraptionSyncState {

    /**
     * 最近一次参与同步的装置对象引用。
     *
     * 当该引用发生变化时，说明实体绑定的装置发生切换，
     * 需要在下一次物理同步中重建碰撞形状与索引映射。
     */
    private var lastContraption: Contraption? = null

    /**
     * 碰撞形状脏标记。
     *
     * `true` 表示需要在物理线程中进行一次形状重建。
     */
    @Volatile
    private var shapeDirty: Boolean = true

    /**
     * 最近一次同步时记录的位置（世界坐标）。
     *
     * 用于在下一次 onSyncTick 时计算速度。
     */
    var lastPosition: Vec3 = Vec3.ZERO
        private set

    /**
     * 最近一次同步时记录的欧拉角（单位：度）。
     *
     * 用于在下一次 onSyncTick 时计算角速度。
     */
    var lastXRotation: Float = 0f
        private set
    var lastYRotation: Float = 0f
        private set
    var lastZRotation: Float = 0f
        private set
    var lastSecondYRotation: Float = 0f
        private set

    /**
     * 上次同步的时间戳（System.nanoTime），用于计算实际时间间隔。
     */
    var lastSyncNanoTime: Long = System.nanoTime()
        private set

    /**
     * 当前发布的同步快照。
     *
     * 由主线程 onSyncTick 写入，物理线程 onPhysicsTick 读取。
     * 使用 @Volatile 保证可见性，利用不可变 data class 保证原子性。
     */
    @Volatile
    var latestSnapshot: ContraptionSyncSnapshot? = null

    /**
     * 绑定当前装置引用。
     *
     * @return 当装置引用发生变化时返回 `true`，并自动标记形状脏。
     */
    fun bindContraption(contraption: Contraption): Boolean {
        if (lastContraption !== contraption) {
            lastContraption = contraption
            shapeDirty = true
            return true
        }
        return false
    }

    /**
     * 将碰撞形状标记为"待重建"。
     */
    fun markShapeDirty() {
        shapeDirty = true
    }

    /**
     * 读取并清除脏标记。
     *
     * @return 若本次需要执行重建则返回 `true`。
     */
    fun consumeShapeDirty(): Boolean {
        if (!shapeDirty) return false
        shapeDirty = false
        return true
    }

    /**
     * 更新最近一次同步位姿缓存。
     *
     * 同时基于位姿变化计算速度，并发布 [latestSnapshot]。
     *
     * @param position 本次同步的权威位置
     * @param xRotation 本次同步的X旋转（度）
     * @param yRotation 本次同步的Y旋转（度）
     * @param zRotation 本次同步的Z旋转（度）
     * @param secondYRotation 本次同步的第二Y旋转（度）
     */
    fun updateLastTransform(
        position: Vec3,
        xRotation: Float,
        yRotation: Float,
        zRotation: Float,
        secondYRotation: Float
    ) {
        val now = System.nanoTime()
        val dt = (now - lastSyncNanoTime).coerceAtLeast(1) / 1_000_000_000.0

        val velocity = position.subtract(lastPosition).scale(1.0 / dt)
        val angVelX = (xRotation - lastXRotation) / dt.toFloat()
        val angVelY = (yRotation - lastYRotation) / dt.toFloat()
        val angVelZ = (zRotation - lastZRotation) / dt.toFloat()
        val angVelSecondY = (secondYRotation - lastSecondYRotation) / dt.toFloat()

        lastPosition = position
        lastXRotation = xRotation
        lastYRotation = yRotation
        lastZRotation = zRotation
        lastSecondYRotation = secondYRotation
        lastSyncNanoTime = now

        latestSnapshot = ContraptionSyncSnapshot(
            position = position,
            xRot = xRotation,
            yRot = yRotation,
            zRot = zRotation,
            secondYRot = secondYRotation,
            linearVelocity = velocity,
            angularVelX = angVelX,
            angularVelY = angVelY,
            angularVelZ = angVelZ + angVelSecondY,
            nanoTime = now
        )
    }

    /**
     * 重置同步状态（首次创建或重建时调用）。
     */
    fun reset() {
        lastPosition = Vec3.ZERO
        lastXRotation = 0f
        lastYRotation = 0f
        lastZRotation = 0f
        lastSecondYRotation = 0f
        lastSyncNanoTime = System.nanoTime()
        latestSnapshot = null
    }
}
