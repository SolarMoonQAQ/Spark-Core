package cn.solarmoon.spark_core.js.physics

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.getFunctionMember
import cn.solarmoon.spark_core.js.getMember
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.PhysicsEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

class JSPhysicsCollisionObject(
    val js: SparkJS,
    val pco: PhysicsCollisionObject
) {

    private val callbackFunctions = mutableMapOf<String, MutableList<Scriptable>>()

    @HostAccess.Export
    fun onCollide(id: String, consumer: Scriptable) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            pco.addCollisionCallback(object : CollisionCallback {
                override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
                    values.forEach {
                        it.getFunctionMember("onStart")?.call(js, JSPhysicsCollisionObject(js, o1), JSPhysicsCollisionObject(js, o2), manifoldId)
                    }
                }

                override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
                    values.forEach {
                        it.getFunctionMember("onProcessed")?.call(js, JSPhysicsCollisionObject(js, o1), JSPhysicsCollisionObject(js, o2), manifoldId)
                    }
                }

                override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
                    values.forEach {
                        it.getFunctionMember("onEnded")?.call(js, JSPhysicsCollisionObject(js, o1), JSPhysicsCollisionObject(js, o2), manifoldId)
                    }
                }
            })
        }

        values.add(consumer)
    }

    @HostAccess.Export
    fun onAttackCollide(id: String, consumer: Scriptable) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            pco.addCollisionCallback(object : AttackCollisionCallback {
                override val attackSystem: AttackSystem = AttackSystem()
                override fun preAttack(
                    isFirst: Boolean,
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    manifoldId: Long
                ) {
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            it.getFunctionMember("preAttack")?.call(js, isFirst, attacker, target, JSPhysicsCollisionObject(js, aBody), JSPhysicsCollisionObject(js, bBody), manifoldId)
                        }
                    }
                }

                override fun doAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    manifoldId: Long
                ): Boolean {
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            it.getFunctionMember("doAttack")?.call(js, attacker, target, JSPhysicsCollisionObject(js, aBody), JSPhysicsCollisionObject(js, bBody), manifoldId)
                        }
                    }
                    return true
                }

                override fun postAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    manifoldId: Long
                ) {
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            it.getFunctionMember("postAttack")?.call(js, attacker, target, JSPhysicsCollisionObject(js, aBody), JSPhysicsCollisionObject(js, bBody), manifoldId)
                        }
                    }
                }
            })
        }

        values.add(consumer)
    }

    @HostAccess.Export
    fun setCollideWithGroups(value: Int) {
        pco.collideWithGroups = value
    }

    @HostAccess.Export
    fun onCollisionActive(consumer: Function) {
        pco.onEvent<PhysicsEvent.OnCollisionActive> {
            consumer.call(js)
        }
    }

    @HostAccess.Export
    fun onCollisionInactive(consumer: Function) {
        pco.onEvent<PhysicsEvent.OnCollisionInactive> {
            consumer.call(js)
        }
    }

    @HostAccess.Export
    fun remove() {
        pco.owner.removeBody(pco.name)
    }

}