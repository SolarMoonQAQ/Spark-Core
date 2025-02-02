package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.Bone
import cn.solarmoon.spark_core.physics.collision.BodyPhysicsTicker
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBQuaternion
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.physics.toRadians
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsBody
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Transform
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Quaterniond
import org.joml.Quaternionf
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div

class MoveWithAnimatedBoneTicker(
    val boneName: String
): BodyPhysicsTicker {

    override fun physicsTick(
        body: PhysicsCollisionObject,
        level: PhysicsLevel
    ) {
        val animatable = body.owner as? IAnimatable<*> ?: return
        if (body is PhysicsRigidBody) {
            body.setPhysicsLocation(animatable.getWorldBonePivot(boneName).toBVector3f())
            body.setPhysicsRotation(animatable.getWorldBoneMatrix(boneName).getUnnormalizedRotation(Quaternionf()).toBQuaternion())
        }
    }

    override fun mcTick(
        body: PhysicsCollisionObject,
        level: Level
    ) {

    }

}