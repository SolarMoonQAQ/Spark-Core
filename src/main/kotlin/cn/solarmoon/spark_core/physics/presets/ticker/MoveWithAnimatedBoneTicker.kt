package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.host.getBody
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBQuaternion
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.neoforge.client.event.ViewportEvent
import org.joml.Matrix4f
import org.joml.Quaternionf

open class MoveWithAnimatedBoneTicker(
    val boneName: String
): PhysicsCollisionObjectTicker {

    var lastPos = Vector3f()

    override fun physicsTick(
        body: PhysicsCollisionObject,
        level: PhysicsLevel
    ) {
        val animatable = body.owner as? IAnimatable<*> ?: return

        if (body is PhysicsRigidBody) {
            val shape = body.collisionShape as? CompoundCollisionShape ?: return
            shape.listChildren().forEach {
                val cube = animatable.model.getBone(boneName)!!.cubes.getOrNull(it.cubeBound.first) ?: return@forEach
                val space = animatable.getSpaceBoneMatrix(boneName)
                shape.setChildTransform(it.shape,
                    Transform(
                        cube.getTransformedCenter(space).toBVector3f(),
                        cube.getTransformedRotation(space).toBQuaternion(),
                        org.joml.Vector3f(1f).toBVector3f()
                    )
                )
            }
            body.setPhysicsScale(animatable.getBone(boneName).getScale().toBVector3f())
        }
    }

    override fun mcTick(
        body: PhysicsCollisionObject,
        level: Level
    ) {
        val animatable = body.owner as? IEntityAnimatable<*> ?: return
        val entity = animatable.animatable
        if (body is PhysicsRigidBody) {
            val targetPos = entity.position().toBVector3f()
            val v = targetPos.subtract(lastPos).mult(20f)
            level.physicsLevel.submitTask {
                body.setLinearVelocity(v)
                body.setPhysicsLocation(animatable.getWorldPosition(1f).toBVector3f())
                body.setPhysicsRotation(animatable.getWorldPositionMatrix(1f).getUnnormalizedRotation(Quaternionf()).toBQuaternion())
            }
            lastPos = targetPos
        }
    }

}