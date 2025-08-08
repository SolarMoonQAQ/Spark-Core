package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.toVec3
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.div
import cn.solarmoon.spark_core.util.toVec3
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import java.util.UUID

object JSPhysicsHelper: JSComponent() {

    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: NativeArray, offset: NativeArray) = createCollisionBoxBoundToBone(animatable, boneName, size, offset, null)

    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: NativeArray, offset: NativeArray, init: Function?): PhysicsCollisionObject {
        val animatable = animatable as? IEntityAnimatable<*> ?: throw IllegalArgumentException("动画体必须是实体类型！")
        val entity = animatable.animatable
        return entity.bindBody(
            PhysicsRigidBody(UUID.randomUUID().toString(), entity, BoxCollisionShape(size.toVec3().div(2.0).toBVector3f()))
        ) {
            isContactResponse = false
            isKinematic = true
            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
            setGravity(Vector3f())
            addPhysicsTicker(MoveWithAnimatedBoneTicker(boneName, offset.toVec3().toBVector3f()))
            init?.call(engine, this)
        }
    }

    fun getContactPosA(manifoldId: Long) = Vector3f().apply { ManifoldPoints.getPositionWorldOnA(manifoldId, this) }.toVec3()

    fun getContactPosB(manifoldId: Long) = Vector3f().apply { ManifoldPoints.getPositionWorldOnB(manifoldId, this) }.toVec3()

    /**
     * 创建一个新的 AttackSystem 实例，可以用于管理攻击状态
     * @return 新的 AttackSystem 实例
     */
    fun createAttackSystem(): AttackSystem {
        return AttackSystem()
    }

}