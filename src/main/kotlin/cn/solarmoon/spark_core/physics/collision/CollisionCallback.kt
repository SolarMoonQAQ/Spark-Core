package cn.solarmoon.spark_core.physics.collision

import com.jme3.bullet.collision.PhysicsCollisionObject

interface CollisionCallback {

    fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1Point: ManifoldPoint, o2Point: ManifoldPoint, manifoldId: Long) {}

    fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1Point: ManifoldPoint, o2Point: ManifoldPoint, manifoldId: Long) {}

    fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1Point: ManifoldPoint, o2Point: ManifoldPoint, manifoldId: Long) {}

}