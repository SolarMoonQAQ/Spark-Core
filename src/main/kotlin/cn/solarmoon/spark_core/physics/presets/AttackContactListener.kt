package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import com.jme3.bullet.collision.ContactListener
import com.jme3.bullet.collision.PersistentManifolds
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

interface AttackContactListener: ContactListener {

    val attackSystem: AttackSystem

    fun preAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long)

    fun doAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long): Boolean

    fun postAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long)

    override fun onContactStarted(manifoldId: Long) {
        val a = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyAId(manifoldId))
        val b = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))
        val attacker = a.owner as? Entity ?: return
        (b.owner as? Entity)?.apply {
            level().submitImmediateTask {
                attackSystem.customAttack(this) {
                    preAttack(attacker, this@apply, a, b, manifoldId)
                    if (!doAttack(attacker, this@apply, a, b, manifoldId)) return@customAttack false
                    postAttack(attacker, this@apply, a, b, manifoldId)
                    true
                }
            }
        }
    }

}