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
    var v = Vector3f()

    override fun physicsTick(body: PhysicsCollisionObject, level: PhysicsLevel) {
        if (body is PhysicsBody) {
            val entity = body.getOwner<Entity>() ?: return
            val targetPos = entity.boundingBox.center.toBVector3f()

            body.setPhysicsLocation(targetPos)
        }
    }

    override fun mcTick(body: PhysicsCollisionObject, level: Level) {
        if (body is PhysicsBody) {
            val entity = body.getOwner<Entity>() ?: return
            val physLevel = level.physicsLevel
            val targetPos = entity.boundingBox.center.toBVector3f()
            physLevel.submitTask {
                val vd = entity.deltaMovement.scale(22.0).toBVector3f()
//                v = targetPos.subtract(lastPos).mult(20f)
//                body.setPhysicsLocation(targetPos.add(v))
                if (body is PhysicsRigidBody) {
//                    body.setLinearVelocity(v)
//                    SparkCore.LOGGER.info("v: $v vd: $vd pt: ${physLevel.mcPartialTicks}")
                }
                lastPos = targetPos
            }
            val pos = body.getPhysicsLocation(Vector3f()).toVector3f().toVec3()
            entity.level().addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0)
        }
    }

}