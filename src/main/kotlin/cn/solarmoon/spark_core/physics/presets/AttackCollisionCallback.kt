package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity

interface AttackCollisionCallback: CollisionCallback {

    val attackSystem: AttackSystem

    fun preAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long)

    fun doAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long): Boolean

    fun postAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long)

    override fun onStarted(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
        val attacker = o1.owner as? Entity ?: return
        (o2.owner as? Entity)?.apply {
            attackSystem.customAttack(this) {
                preAttack(attacker, this@apply, o1, o2, manifoldId)
                if (!doAttack(attacker, this@apply, o1, o2, manifoldId)) return@customAttack false
                postAttack(attacker, this@apply, o1, o2, manifoldId)
                true
            }
        }
    }

    override fun onEnded(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
        attackSystem.reset()
    }

}