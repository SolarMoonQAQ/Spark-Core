package cn.solarmoon.spark_core.physics.presets.ticker

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBQuaternion
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import net.minecraft.world.level.Level
import org.joml.Quaternionf

class MoveWithBonePivotTicker(boneName: String): MoveWithAnimatedBoneTicker(boneName) {

    override fun physicsTick(
        body: PhysicsCollisionObject,
        level: PhysicsLevel
    ) {
        val animatable = body.owner as? IAnimatable<*> ?: return

        if (body is PhysicsRigidBody) {
            val shape = body.collisionShape as? CompoundCollisionShape ?: return

            shape.listChildren().forEach {
                shape.setChildTransform(it.shape,
                    Transform(
                        animatable.getSpaceBonePivot(boneName).toBVector3f(),
                        animatable.getSpaceBoneMatrix(boneName).getUnnormalizedRotation(Quaternionf()).toBQuaternion(),
                        org.joml.Vector3f(1f).toBVector3f()
                    )
                )
            }
            body.setPhysicsScale(animatable.getBone(boneName).getScale().toBVector3f())
        }
    }

}