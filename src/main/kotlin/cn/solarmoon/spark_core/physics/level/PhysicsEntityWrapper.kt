package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity

sealed class PhysicsEntityWrapper(val mcEntity: Entity) {
    abstract val collisionShape: CollisionShape
    abstract val rigidBody: PhysicsRigidBody

    fun syncToPhysics() {
        mcEntity.position().run {
            rigidBody.setPhysicsLocation(toBVector3f())
        }
    }

    fun syncToMCEntity() {
        rigidBody.getPhysicsLocation(Vector3f()).run {
            mcEntity.setPos(toVec3())
        }
    }

}