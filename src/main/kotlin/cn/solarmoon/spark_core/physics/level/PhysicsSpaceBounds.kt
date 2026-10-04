package cn.solarmoon.spark_core.physics.level

import com.jme3.math.Vector3f

/**
 * 物理空间的世界边界，[PhysicsWorld] 与 [WorldSnapshot] 共用同一组声明。
 *
 * 取原版世界边界尺度：X/Z `±29,999,984`、Y `-1,000 ~ 15,000`。
 *
 * - 这三个值在 float 下**可精确表示**（29,999,984 = 2^25 − 2^4，小于 2^25），声明与数值一致。
 * - [com.jme3.bullet.PhysicsSpace] 的构造函数只接受 `Vector3f`，没有 `Vec3d` 重载；但本项目的
 *   broadphase 固定为 **DBVT**，DBVT 不量化、也不裁剪世界边界，只有 `AXIS_SWEEP_3` /
 *   `AXIS_SWEEP_3_32` 才依赖这组值做 sweep-and-prune 量化。
 *   **因此不要把这里的边界配上 AXIS_SWEEP 系列 broadphase**，否则 2^25 之外的刚体会被量化错位。
 * - Y 的下界远低于原版建筑下限，用于覆盖自定义维度。
 *
 * 每次调用都返回新的实例，避免两个物理空间共享同一份可变 [Vector3f]。
 */
object PhysicsSpaceBounds {

    /** X/Z 方向的边界半径 */
    const val HORIZONTAL = 29_999_984f

    const val MIN_Y = -1_000f

    const val MAX_Y = 15_000f

    fun min(): Vector3f = Vector3f(-HORIZONTAL, MIN_Y, -HORIZONTAL)

    fun max(): Vector3f = Vector3f(HORIZONTAL, MAX_Y, HORIZONTAL)
}
