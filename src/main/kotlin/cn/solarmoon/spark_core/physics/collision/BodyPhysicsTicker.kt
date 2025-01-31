package cn.solarmoon.spark_core.physics.collision

import com.jme3.bullet.collision.PhysicsCollisionObject

interface BodyPhysicsTicker {

    fun physicsTick(body: PhysicsCollisionObject)

}