package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js2.safeGetMember
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import cn.solarmoon.spark_core.physics.collision.PhysicsEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.Value

interface JSPhysicsCollisionObject {

    val pco get() = this as PhysicsCollisionObject

    val callbackFunctions: MutableMap<String, MutableList<Value>>

    fun onCollide(id: String, consumer: Value) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            pco.addCollisionCallback(object : CollisionCallback {
                override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                    values.forEach {
                        it.safeGetMember("onStart")?.execute(o1, o2, o1P, o2P, manifoldId)
                    }
                }

                override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                    values.forEach {
                        it.safeGetMember("onProcessed")?.execute(o1, o2, o1P, o2P, manifoldId)
                    }
                }

                override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                    values.forEach {
                        it.safeGetMember("onEnded")?.execute(o1, o2, o1P, o2P, manifoldId)
                    }
                }
            })
        }

        values.add(consumer)
    }

    fun onAttackCollide(id: String, consumer: Value, customAttackSystem: AttackSystem? = null) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            val attackCallback = object : AttackCollisionCallback {
                override val attackSystem: AttackSystem = customAttackSystem ?: AttackSystem()
                override fun preAttack(
                    isFirst: Boolean,
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    aPoint: ManifoldPoint,
                    bPoint: ManifoldPoint,
                    manifoldId: Long
                ) {
                    if (attacker.level().isClientSide) return
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            it.safeGetMember("preAttack")?.execute(isFirst, attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                        }
                    }
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
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            it.safeGetMember("doAttack")?.execute(attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                        }
                    }
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
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            it.safeGetMember("postAttack")?.execute(attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                        }
                    }
                }
            }
            pco.addCollisionCallback(attackCallback)
        }

        values.add(consumer)
    }

    fun setCollideWithGroups(value: Int) {
        pco.collideWithGroups = value
    }

    fun onCollisionActive(consumer: Value) {
        pco.onEvent<PhysicsEvent.OnCollisionActive> {
            consumer.execute()
        }
    }

    fun onCollisionInactive(consumer: Value) {
        pco.onEvent<PhysicsEvent.OnCollisionInactive> {
            consumer.execute()
        }
    }

    fun remove() {
        pco.owner.removeBody(pco.name)
    }

}