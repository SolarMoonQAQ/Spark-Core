package cn.solarmoon.spark_core.physics.host

import com.jme3.bullet.collision.PhysicsCollisionObject

inline fun <reified T: PhysicsCollisionObject> PhysicsHost.getBody(name: String) = getBody(name, T::class)