package cn.solarmoon.spark_core.physics.component

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.level.Level
import java.util.WeakHashMap

var PhysicsCollisionObject.component: CollisionObjectComponent<*>?
    get() = this.userObject as? CollisionObjectComponent<*>
    private set(value) {
        this.userObject = value
    }

var PhysicsRigidBody.component: RigidBodyComponent?
    get() = this.userObject as? RigidBodyComponent
    private set(value) {
        this.userObject = value
    }

private val collisionObjects = mutableMapOf<Level, MutableMap<Long, CollisionObjectComponent<*>>>()
val Level.allCollisionComponents: Map<Long, CollisionObjectComponent<*>>
    get() = collisionObjects.getOrPut(this) { mutableMapOf() }.toMap()

fun Level.getCollisionComponent(id: Long): CollisionObjectComponent<*>? {
    return allCollisionComponents[id]
}

internal fun Level.addCollisionComponent(component: CollisionObjectComponent<*>) {
    collisionObjects.getOrPut(this) { mutableMapOf() } [component.id] = component
}

internal fun Level.removeCollisionComponent(component: CollisionObjectComponent<*>) {
    removeCollisionComponent(component.id)
}

internal fun Level.removeCollisionComponent(id: Long) {
    collisionObjects[this]?.remove(id)
}
