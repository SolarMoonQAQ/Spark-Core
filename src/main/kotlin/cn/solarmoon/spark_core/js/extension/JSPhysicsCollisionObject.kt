package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.getFunctionMember
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import cn.solarmoon.spark_core.physics.collision.PhysicsEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

interface JSPhysicsCollisionObject {

    val pco get() = this as PhysicsCollisionObject
    val js get() = pco.level!!.mcLevel.jsEngine

    val callbackFunctions: MutableMap<String, MutableList<Scriptable>>

    fun onCollide(id: String, consumer: Scriptable) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            pco.addCollisionCallback(object : CollisionCallback {
                override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                    values.forEach {
                        it.getFunctionMember("onStart")?.call(js, o1, o2, o1P, o2P, manifoldId)
                    }
                }

                override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                    values.forEach {
                        it.getFunctionMember("onProcessed")?.call(js, o1, o2, o1P, o2P, manifoldId)
                    }
                }

                override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                    values.forEach {
                        it.getFunctionMember("onEnded")?.call(js, o1, o2, o1P, o2P, manifoldId)
                    }
                }
            })
        }

        values.add(consumer)
    }

    fun onAttackCollide(id: String, consumer: Scriptable, customAttackSystem: AttackSystem? = null) {
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
                            it.getFunctionMember("preAttack")?.call(js, isFirst, attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
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
                            it.getFunctionMember("doAttack")?.call(js, attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
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
                            it.getFunctionMember("postAttack")?.call(js, attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
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

    fun onCollisionActive(consumer: Function) {
        pco.onEvent<PhysicsEvent.OnCollisionActive> {
            consumer.call(js)
        }
    }

    fun onCollisionInactive(consumer: Function) {
        pco.onEvent<PhysicsEvent.OnCollisionInactive> {
            consumer.call(js)
        }
    }

    fun remove() {
        pco.owner.removeBody(pco.name)
    }

}