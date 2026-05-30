package cn.solarmoon.spark_core.physics

import com.jme3.bullet.collision.PhysicsCollisionObject

/**
 * 穿透去重的唯一密钥。
 *
 * 组合物理体 owner 和区域标识，用于投射物穿透去重集合。
 * 同一次飞行中，同一 (owner, zoneId) 不会被重复判定。
 *
 * @param owner  物理体所属者（[PhysicsHost] 实例，如 SubPart、Entity）
 * @param zoneId 区域标识字符串。
 *               null 表示整个物理体作为一个不可分割的判定目标（如普通 Entity）；
 *               non-null 表示物理体上的某个子区域（如 SubPart 的 HitBox id），
 *               不同区域可以被独立穿透且不互相影响。
 */
data class PenetrationKey(
    val owner: PhysicsHost,
    val zoneId: String?,
) {
    companion object {

        /**
         * 从碰撞检测结果构造密钥。
         *
         * 便捷工厂方法：从 [PhysicsCollisionObject] 中提取 owner，
         * 调用 [PhysicsHost.getPenetrationKey] 生成穿透密钥。
         *
         * @param body          发生碰撞的物理体
         * @param triangleIndex 碰撞的三角形索引（仅对 CompoundCollisionShape 有效）
         * @return 穿透密钥；owner 为 null（如命中地形）时返回 null，表示无需去重
         */
        @JvmStatic
        fun fromCollision(body: PhysicsCollisionObject, triangleIndex: Int): PenetrationKey? {
            val owner = body.userObject as? PhysicsHost ?: return null
            return owner.getPenetrationKey(body, triangleIndex)
        }
    }
}
