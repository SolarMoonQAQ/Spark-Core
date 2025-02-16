package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.physics.collision.BodyPhysicsTicker
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBMatrix3f
import cn.solarmoon.spark_core.physics.toRadians
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Matrix3f

class RotateAroundHostTicker: BodyPhysicsTicker {

    override fun physicsTick(
        body: PhysicsCollisionObject,
        level: PhysicsLevel
    ) {
        if (body is PhysicsRigidBody) {
            val owner = body.owner
            if (owner is Entity) {
                body.setPhysicsRotation(Matrix3f().rotateY(owner.yRot.toRadians()).toBMatrix3f())
            }
        }
    }

    override fun mcTick(
        body: PhysicsCollisionObject,
        level: Level
    ) {
    }

}