package cn.solarmoon.spark_core.entity.attack

import cn.solarmoon.spark_core.physics.ManifoldPoint
import com.jme3.bullet.collision.PhysicsCollisionObject

open class CollisionAttackContext(
    val o1: PhysicsCollisionObject,
    val o2: PhysicsCollisionObject,
    val o1Point: ManifoldPoint,
    val o2Point: ManifoldPoint
)