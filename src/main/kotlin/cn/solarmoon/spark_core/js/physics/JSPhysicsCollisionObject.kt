package cn.solarmoon.spark_core.js.physics

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.PhysicsEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

class JSPhysicsCollisionObject(
    val pco: PhysicsCollisionObject
) {

    private val callbackFunctions = mutableMapOf<String, MutableList<Value>>()

    @HostAccess.Export
    fun onCollide(id: String, consumer: Value) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            pco.addCollisionCallback(object : CollisionCallback {
                override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
                    values.forEach {
                        it.getMember("onStart").execute(JSPhysicsCollisionObject(o1), JSPhysicsCollisionObject(o2), manifoldId)
                    }
                }

                override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
                    values.forEach {
                        it.getMember("onProcessed").execute(JSPhysicsCollisionObject(o1), JSPhysicsCollisionObject(o2), manifoldId)
                    }
                }

                override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
                    values.forEach {
                        it.getMember("onEnded").execute(JSPhysicsCollisionObject(o1), JSPhysicsCollisionObject(o2), manifoldId)
                    }
                }
            })
        }

        values.add(consumer)
    }

    @HostAccess.Export
    fun onAttackCollide(id: String, consumer: Value, condition: Value) {
        val values = callbackFunctions.getOrPut(id) { mutableListOf() }

        if (values.isEmpty()) {
            pco.addCollisionCallback(object : AttackCollisionCallback {
                override val attackSystem: AttackSystem = AttackSystem()
                override fun preAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    manifoldId: Long
                ) {
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        values.forEach {
                            if (it.hasMember("preAttack")) it.getMember("preAttack").execute(attacker, target, JSPhysicsCollisionObject(aBody), JSPhysicsCollisionObject(bBody), manifoldId)
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
                            if (it.hasMember("doAttack")) it.getMember("doAttack").execute(attacker, target, JSPhysicsCollisionObject(aBody), JSPhysicsCollisionObject(bBody), manifoldId)
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
                            if (it.hasMember("postAttack")) it.getMember("postAttack").execute(attacker, target, JSPhysicsCollisionObject(aBody), JSPhysicsCollisionObject(bBody), manifoldId)
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
    fun onCollisionActive(consumer: Value) {
        pco.onEvent<PhysicsEvent.OnCollisionActive> {
            consumer.execute()
        }
    }

    @HostAccess.Export
    fun onCollisionInactive(consumer: Value) {
        pco.onEvent<PhysicsEvent.OnCollisionInactive> {
            consumer.execute()
        }
    }

    @HostAccess.Export
    fun remove() {
        pco.owner.removeBody(pco.name)
    }

}