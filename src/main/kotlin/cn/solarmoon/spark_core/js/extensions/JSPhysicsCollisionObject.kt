package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.doc.JSClass
import cn.solarmoon.spark_core.js.execute
import cn.solarmoon.spark_core.js.getFunctionMember
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import cn.solarmoon.spark_core.physics.collision.PhysicsObjectEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

@JSClass("PhysicsCollisionObject")
interface JSPhysicsCollisionObject {

    val pco get() = this as PhysicsCollisionObject

    fun onCollide(id: String, consumer: Function) {
        pco.addCollisionCallback(object : CollisionCallback {
            override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                consumer.getFunctionMember("onStarted")?.execute(o1, o2, o1P, o2P, manifoldId)
            }

            override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                consumer.getFunctionMember("onProcessed")?.execute(o1, o2, o1P, o2P, manifoldId)
            }

            override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                consumer.getFunctionMember("onEnded")?.execute(o1, o2, o1P, o2P, manifoldId)
            }
        })
    }

    fun onAttackCollide(id: String, consumer: Function, customAttackSystem: AttackSystem? = null) {
        pco.addCollisionCallback(
            object : AttackCollisionCallback {
                override val attackSystem: AttackSystem = customAttackSystem ?: AttackSystem()
                override fun preAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    aPoint: ManifoldPoint,
                    bPoint: ManifoldPoint,
                    manifoldId: Long
                ) {
                    if (attacker.level().isClientSide) return
                    consumer.getFunctionMember("preAttack")?.execute(attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                }

                override fun doAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    aPoint: ManifoldPoint,
                    bPoint: ManifoldPoint,
                    manifoldId: Long
                ): Boolean {
                    if (attacker.level().isClientSide) return false
                    consumer.getFunctionMember("doAttack")?.execute(attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                    return true
                }

                override fun postAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    aPoint: ManifoldPoint,
                    bPoint: ManifoldPoint,
                    manifoldId: Long
                ) {
                    if (attacker.level().isClientSide) return
                    consumer.getFunctionMember("postAttack")?.execute(attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                }
            }
        )
    }

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