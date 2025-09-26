package cn.solarmoon.spark_core.physics.body

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Transform

class PhysicsBodyState(
    private val body: PhysicsCollisionObject
) {

    var lastTransform = Transform()
    var transform = Transform()

    fun update() {
        lastTransform = transform
        transform = body.getTransform(null)
    }

}