package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject

interface PhysicsHost {

    val physicsLevel: PhysicsLevel

    val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject>

    fun getPhysicsBody(name: String): PhysicsCollisionObject? {
        return allPhysicsBodies[name]
    }

    /**
     * 返回此物理体在指定三角形索引处的穿透区域标识。
     *
     * 用于投射物穿透去重：同一次飞行中，同一 (owner, zoneId) 不会被重复判定。
     * 返回 null 表示整个物理体作为单一不可分割的判定目标（如普通 Entity）。
     * 返回 non-null 字符串表示此三角形属于某个独立穿透区域（如 SubPart 的 HitBox id），
     * 不同区域可被独立穿透且不互相影响。
     *
     * 默认实现返回 null（整个刚体作为一个区域）。
     * 需要子区域穿透判定的实现者（如 SubPart）应覆写此方法。
     *
     * @param body          发生碰撞的物理体
     * @param triangleIndex 碰撞的三角形索引（仅对 CompoundCollisionShape 有效）
     * @return 区域标识字符串，null 表示整刚体为单区域
     */
    fun getPenetrationZoneId(body: PhysicsCollisionObject, triangleIndex: Int): String? {
        return null
    }

    /**
     * 生成穿透去重的唯一密钥。
     *
     * 组合当前 owner 身份和 [getPenetrationZoneId] 返回的区域标识，
     * 构造 [PenetrationKey]。调用方将此密钥放入 HashSet 即可实现穿透去重。
     *
     * @param body          发生碰撞的物理体
     * @param triangleIndex 碰撞的三角形索引
     * @return 穿透密钥
     */
    fun getPenetrationKey(body: PhysicsCollisionObject, triangleIndex: Int): PenetrationKey {
        return PenetrationKey(this, getPenetrationZoneId(body, triangleIndex))
    }

}