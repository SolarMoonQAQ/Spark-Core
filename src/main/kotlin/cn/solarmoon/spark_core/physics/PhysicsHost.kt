package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.sync.Syncer
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.level.Level

interface PhysicsHost: Syncer {

    val physicsLevel: PhysicsLevel

    val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject>

    fun getPhysicsBody(name: String): PhysicsCollisionObject? {
        return allPhysicsBodies[name]
    }

}