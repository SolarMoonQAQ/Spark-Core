package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.getFunctionMember
import cn.solarmoon.spark_core.lua.callField
import cn.solarmoon.spark_core.lua.doc.LuaClass
import cn.solarmoon.spark_core.lua.execute
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import cn.solarmoon.spark_core.physics.collision.PhysicsObjectEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import cn.solarmoon.spark_core.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.world.entity.Entity
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable

@LuaClass("PhysicsCollisionObject")
interface LuaPhysicsCollisionObject {

    val pco get() = this as PhysicsCollisionObject

    fun onCollide(id: String, consumer: LuaValueProxy) {
        pco.addCollisionCallback(object : CollisionCallback {
            override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                consumer.callField("onStarted", o1, o2, o1P, o2P, manifoldId)
            }

            override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                consumer.callField("onProcessed", o1, o2, o1P, o2P, manifoldId)
            }

            override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1P: ManifoldPoint, o2P: ManifoldPoint, manifoldId: Long) {
                consumer.callField("onEnded", o1, o2, o1P, o2P, manifoldId)
            }
        })
    }

    fun onAttackCollide(id: String, consumer: LuaValueProxy, customAttackSystem: AttackSystem? = null) {
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
                    attacker.level().submitImmediateTask(PPhase.POST) {
                        consumer.callField("preAttack", attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
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
                        consumer.callField("doAttack", attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
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
                        consumer.callField("postAttack", attacker, target, aBody, bBody, aPoint, bPoint, manifoldId, attackSystem)
                    }
                }
            }
        )
    }

    fun setCollideWithGroups(value: Int) {
        pco.collideWithGroups = value
    }

    fun onCollisionActive(consumer: LuaValueProxy) {
        pco.onEvent<PhysicsObjectEvent.OnCollisionActive> {
            consumer.execute()
        }
    }

    fun onCollisionInactive(consumer: LuaValueProxy) {
        pco.onEvent<PhysicsObjectEvent.OnCollisionInactive> {
            consumer.execute()
        }
    }

    fun remove() {
        pco.owner.removeBody(pco.name)
    }

}