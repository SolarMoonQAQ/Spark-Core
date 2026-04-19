package cn.solarmoon.spark_core.compat.create

import com.simibubi.create.content.contraptions.Contraption
import net.minecraft.world.phys.Vec3

/**
 * Create 装置物理同步状态缓存。
 *
 * 作用：
 * 1) 缓存“碰撞形状是否需要重建”的脏标记；
 * 2) 缓存最近一次参与同步的装置引用与位姿；
 * 3) 为物理线程中的联动逻辑提供轻量状态容器。
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
     */
    var lastPosition: Vec3 = Vec3.ZERO
        private set

    /**
     * 最近一次同步时记录的欧拉角（单位：度）。
     *
     * 分别对应 rotationState 中的 x/y/z 与 secondYRotation。
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
     * 将碰撞形状标记为“待重建”。
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
     */
    fun updateLastTransform(
        position: Vec3,
        xRotation: Float,
        yRotation: Float,
        zRotation: Float,
        secondYRotation: Float
    ) {
        lastPosition = position
        lastXRotation = xRotation
        lastYRotation = yRotation
        lastZRotation = zRotation
        lastSecondYRotation = secondYRotation
    }
}

