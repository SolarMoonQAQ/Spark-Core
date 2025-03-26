package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBMatrix3f
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.TaskSubmitOffice
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Matrix3f

class RotateAroundHostTicker: PhysicsCollisionObjectTicker {

    var lastPos = Vector3f()

    override fun prePhysicsTick(
        body: PhysicsCollisionObject,
        level: PhysicsLevel
    ) {
        if (body is PhysicsRigidBody) {
            val owner = body.owner
            if (owner is Entity) {
                body.setPhysicsLocation(owner.position().toBVector3f())
                body.setPhysicsRotation(Matrix3f().rotateY(Mth.wrapDegrees(owner.yRot).toRadians()).toBMatrix3f())

                val targetPos = owner.position().toBVector3f()
                val v = targetPos.subtract(lastPos).mult(20f)
                body.setLinearVelocity(v)
                lastPos = targetPos
            }
        }
    }

}