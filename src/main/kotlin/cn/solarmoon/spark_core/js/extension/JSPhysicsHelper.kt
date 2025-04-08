package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.anim.JSAnimatable
import cn.solarmoon.spark_core.js.physics.JSPhysicsCollisionObject
import cn.solarmoon.spark_core.physics.div
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.graalvm.polyglot.HostAccess
import java.util.UUID
import kotlin.uuid.Uuid

class JSPhysicsHelper(
    val js: SparkJS
) {

    @HostAccess.Export
    fun createCollisionBoxBoundToBone(animatable: JSAnimatable, boneName: String, size: Vec3, offset: Vec3): JSPhysicsCollisionObject {
        val animatable = animatable.animatable as? IEntityAnimatable<*> ?: throw IllegalArgumentException("动画体必须是实体类型！")
        val entity = animatable.animatable
        return JSPhysicsCollisionObject(js,
            entity.bindBody(PhysicsRigidBody(UUID.randomUUID().toString(), entity, BoxCollisionShape(size.div(2.0).toBVector3f()))) {
                isContactResponse = false
                isKinematic = true
                collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
                setGravity(Vector3f())
                addPhysicsTicker(MoveWithAnimatedBoneTicker(boneName, offset.toBVector3f()))
            }
        )
    }

}