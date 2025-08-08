package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.PPhase
import cn.solarmoon.spark_core.util.toBQuaternion
import cn.solarmoon.spark_core.util.toVec3
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

    val lastPos = Vector3f()

    override fun mcTick(body: PhysicsCollisionObject, level: Level) {
        val entity = body.owner as? Entity ?: return
        val animatable = body.owner as? IAnimatable<*> ?: return
        if (body is PhysicsRigidBody) {
            entity.level().physicsLevel.submitImmediateTask(PPhase.PRE) {
                val currentPos = entity.position().toBVector3f()
                val previousPos = lastPos
                lastPos.set(currentPos)
                try {
                    body.setPhysicsTransform(Transform(
                        animatable.getWorldBonePivot(boneName, offset.toVec3()).toBVector3f(),
                        animatable.getWorldBoneMatrix(boneName).getUnnormalizedRotation(Quaternionf()).toBQuaternion(),
                        animatable.getBonePose(boneName).getScale().toBVector3f()
                    ))
                } catch (e: Exception) {
                    println(e)
                }
                val v = currentPos.subtract(previousPos).mult(20f)
                body.setLinearVelocity(v)
            }
        }
    }

}