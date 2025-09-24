package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.component.CollisionObjectComponent
import cn.solarmoon.spark_core.sync.Syncer

interface PhysicsHost: Syncer {

    val physicsLevel: PhysicsLevel

    val allCollisionObjects: MutableMap<String, CollisionObjectComponent<*>>

    fun getPhysicsBody(name: String): CollisionObjectComponent<*>? {
        return allCollisionObjects[name]
    }

}