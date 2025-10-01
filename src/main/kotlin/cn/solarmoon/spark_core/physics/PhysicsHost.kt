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

    fun createPhysicsBody(body: PhysicsCollisionObject): PhysicsCollisionObject {
        body.owner = this
        return body
    }

    fun createPhysicsBody(body: PhysicsCollisionObject, name: String): PhysicsCollisionObject {
        body.name = name
        body.owner = this
        return body
    }

    fun createPhysicsBody(shape: CollisionShape, mass: Float): PhysicsRigidBody {
        val body = PhysicsRigidBody(shape, mass)
        body.owner = this
        return body
    }

    fun createPhysicsBody(shape: CollisionShape, mass: Float, name: String): PhysicsRigidBody {
        val body = PhysicsRigidBody(shape, mass)
        body.name = name
        body.owner = this
        return body
    }

    fun removePhysicsBody(body: PhysicsCollisionObject?) {
        if (body == null) return
        removePhysicsBodyFromWorld(body)
        allPhysicsBodies.remove(body.name)
    }

    fun removePhysicsBodyFromWorld(body: PhysicsCollisionObject?) {
        if (body == null || !body.isInWorld) return
        if (body.owner != this) SparkCore.LOGGER.warn("Physics collision object $body is not owned by $this")
        physicsLevel.apply {
            submitImmediateTask {
                world.removeCollisionObject(body)
            }
        }
    }

    fun removePhysicsBody(name: String) {
        removePhysicsBodyFromWorld(name)
        allPhysicsBodies.remove(name)
    }

    fun removePhysicsBodyFromWorld(name: String) {
        val body = getPhysicsBody(name)
        if (body != null && body.isInWorld) {
            physicsLevel.apply {
                submitImmediateTask {
                    world.removeCollisionObject(body)
                }
            }
        } else SparkCore.LOGGER.error("Physics collision object $name not found in $this")
    }

    fun addPhysicsBody(body: PhysicsCollisionObject) {
        if (body.owner != this) {
            SparkCore.LOGGER.warn("Physics collision object $body is not owned by $this, changing owner to $this")
        }
        physicsLevel.apply {
            submitImmediateTask {
                world.addCollisionObject(body)
            }
        }
    }


}