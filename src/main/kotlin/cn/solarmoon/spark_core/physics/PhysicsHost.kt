package cn.solarmoon.spark_core.physics

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

    fun getPhysicsBody(uuid: String): PhysicsCollisionObject? {
        return allPhysicsBodies[uuid]
    }

    fun createPhysicsBody(body: PhysicsCollisionObject): PhysicsCollisionObject {
        body.owner = this
        return body
    }

    fun createPhysicsBody(shape: CollisionShape, mass: Float): PhysicsRigidBody {
        val body = PhysicsRigidBody(shape, mass)
        body.owner = this
        return body
    }

    fun removePhysicsBody(body: PhysicsCollisionObject) {
        physicsLevel.apply {
            submitImmediateTask {
                world.removeCollisionObject(body)
            }
        }
    }

    fun addPhysicsBody(body: PhysicsCollisionObject) {
        physicsLevel.apply {
            submitImmediateTask {
                world.addCollisionObject(body)
            }
        }
    }


}