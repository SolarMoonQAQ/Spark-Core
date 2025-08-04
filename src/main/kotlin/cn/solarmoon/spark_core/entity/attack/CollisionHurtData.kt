package cn.solarmoon.spark_core.entity.attack

import com.jme3.bullet.collision.PhysicsCollisionObject

/**
 * 受击信息，不包含伤害源和伤害值，如果想调用这两个以检测直接找Entity的hurt方法插入即可
 * @param attackBody 触发该次攻击的几何体，可以通过几何体大小位置等信息实现想要的效果
 * @param damagedBody 该次攻击的几何体所击中的骨骼
 */
data class CollisionHurtData(
    val attackBody: PhysicsCollisionObject,
    val damagedBody: PhysicsCollisionObject,
    val manifoldId: Long
) {

}