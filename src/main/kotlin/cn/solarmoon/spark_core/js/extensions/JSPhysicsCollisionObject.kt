package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.js.doc.JSClass
import cn.solarmoon.spark_core.physics.collision.PhysicsObjectEvent
import cn.solarmoon.spark_core.physics.onEvent
import com.jme3.bullet.collision.PhysicsCollisionObject

@JSClass("PhysicsCollisionObject")
interface JSPhysicsCollisionObject {

    val pco get() = this as PhysicsCollisionObject

//    fun onCollide(id: String, consumer: IV8ValueObject) {
//        pco.addCollisionCallback(object : CollisionCallback {
//            override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
//                consumer.invokeVoid("onStarted", o1, o2, o1P, o2P, manifoldId)
//            }
//
//            override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
//                consumer.invokeVoid("onProcessed", o1, o2, o1P, o2P, manifoldId)
//            }
//
//            override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
//                consumer.invokeVoid("onEnded", o1, o2, o1P, o2P, manifoldId)
//            }
//        })
//    }
//
//    fun onAttackCollide(id: String, consumer: IV8ValueObject, customAttackSystem: AttackSystem? = null) {
//        pco.addCollisionCallback(
//            object : AttackCollisionCallback {
//                override val attackSystem: AttackSystem = customAttackSystem ?: AttackSystem()
//                override fun preAttack(
//                    attacker: Entity,
//                    target: Entity,
//                    aBody: PhysicsCollisionObject,
//                    bBody: PhysicsCollisionObject,
//                    aPoint: ManifoldPoint,
//                    bPoint: ManifoldPoint,
//                    manifoldId: Long
//                ) {
//                    if (attacker.level().isClientSide) return
//                    consumer.invokeVoid("preAttack", attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
//                }
//
//                override fun doAttack(
//                    attacker: Entity,
//                    target: Entity,
//                    aBody: PhysicsCollisionObject,
//                    bBody: PhysicsCollisionObject,
//                    aPoint: ManifoldPoint,
//                    bPoint: ManifoldPoint,
//                    manifoldId: Long
//                ): Boolean {
//                    if (attacker.level().isClientSide) return false
//                    consumer.invokeVoid("doAttack", attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
//                    return true
//                }
//
//                override fun postAttack(
//                    attacker: Entity,
//                    target: Entity,
//                    aBody: PhysicsCollisionObject,
//                    bBody: PhysicsCollisionObject,
//                    aPoint: ManifoldPoint,
//                    bPoint: ManifoldPoint,
//                    manifoldId: Long
//                ) {
//                    if (attacker.level().isClientSide) return
//                    consumer.invokeVoid("postAttack", attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
//                }
//            }
//        )
//    }

    fun setCollideWithGroups(value: Int) {
        pco.collideWithGroups = value
    }

    fun onCollisionActive(consumer: () -> Unit) {
        pco.onEvent<PhysicsObjectEvent.OnCollisionActive> {
            consumer()
        }
    }

    fun onCollisionInactive(consumer: () -> Unit) {
        pco.onEvent<PhysicsObjectEvent.OnCollisionInactive> {
            consumer()
        }
    }

    fun remove() {
        pco.owner.removeBody(pco.name)
    }

}