package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.sync.Syncer
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.level.Level

interface PhysicsHost {

    val physicsLevel: PhysicsLevel

    val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject>

    fun getPhysicsBody(name: String): PhysicsCollisionObject? {
        return allPhysicsBodies[name]
    }

}