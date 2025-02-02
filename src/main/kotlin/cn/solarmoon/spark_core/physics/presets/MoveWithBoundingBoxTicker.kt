package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.collision.BodyPhysicsTicker
import cn.solarmoon.spark_core.physics.getOwner
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3

class MoveWithBoundingBoxTicker : BodyPhysicsTicker {

    var lastPos = Vector3f()

    override fun physicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {

    }

    override fun mcTick(body: PhysicsCollisionObject, level: Level) {
        if (body is PhysicsBody) {
            val entity = body.getOwner<Entity>() ?: return
            val physLevel = level.physicsLevel
            val targetPos = entity.boundingBox.center.toBVector3f()
            physLevel.submitTask {
                val v = targetPos.subtract(lastPos)
                body.setPhysicsLocation(targetPos)
                if (body is PhysicsRigidBody) {
                    body.setLinearVelocity(v)
                }
            }
            lastPos = targetPos
        }
    }

}