package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.js.doc.JSGlobal

@JSGlobal("PhysicsCollisionObject")
object JSPhysicsCollisionObjectGlobal {

//    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: DoubleArray, offset: DoubleArray, init: ((PhysicsCollisionObject) -> Unit)?): PhysicsCollisionObject {
//        val animatable = animatable as? IEntityAnimatable<*> ?: throw IllegalArgumentException("动画体必须是实体类型！")
//        val entity = animatable.animatable
//        return entity.bindBody(
//            PhysicsRigidBody(UUID.randomUUID().toString(), entity, BoxCollisionShape(size.toVec3().div(2.0).toBVector3f()))
//        ) {
//            isContactResponse = false
//            isKinematic = true
//            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
//            setGravity(Vector3f())
//            addPhysicsTicker(MoveWithAnimatedBoneTicker(boneName, offset.toVec3().toBVector3f()))
//            init?.invoke(this)
//        }
//    }

}