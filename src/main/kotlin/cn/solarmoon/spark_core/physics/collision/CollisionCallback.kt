package cn.solarmoon.spark_core.physics.collision

import com.jme3.bullet.collision.PhysicsCollisionObject

interface CollisionCallback {

    fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {}

    fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, hitPointWorld: com.jme3.math.Vector3f, hitNormalWorld: com.jme3.math.Vector3f, impulse: Float) {}

    fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {}

}