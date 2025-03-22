package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBQuaternion
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Quaternionf

open class MoveWithAnimatedBoneTicker(
    val boneName: String,
    val offset: Vector3f = Vector3f()
): PhysicsCollisionObjectTicker {

    var lastPos = Vector3f()

    override fun physicsTick(
        body: PhysicsCollisionObject,
        level: PhysicsLevel
    ) {
        val animatable = body.owner as? IAnimatable<*> ?: return
        if (body is PhysicsRigidBody) {
            body.setPhysicsTransform(Transform(
                animatable.getWorldBonePivot(boneName, offset.toVec3()).toBVector3f(),
                animatable.getWorldBoneMatrix(boneName).getUnnormalizedRotation(Quaternionf()).toBQuaternion(),
                animatable.getBone(boneName).getScale().toBVector3f()
            ))
        }
    }

    override fun mcTick(
        body: PhysicsCollisionObject,
        level: Level
    ) {
        val entity = body.owner as? Entity ?: return
        if (body is PhysicsRigidBody) {
            val currentPos = entity.position().toBVector3f()
            val previousPos = lastPos
            lastPos = currentPos
            level.physicsLevel.submitImmediateTask {
                val v = currentPos.subtract(previousPos).mult(20f)
                body.setLinearVelocity(v)
            }
        }
    }

}