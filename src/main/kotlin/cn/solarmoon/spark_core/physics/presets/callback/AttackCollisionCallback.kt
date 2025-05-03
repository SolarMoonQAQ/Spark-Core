package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.toVec3
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Vector3f
import net.minecraft.world.entity.Entity

interface AttackCollisionCallback: CollisionCallback {

    val attackSystem: AttackSystem

    fun preAttack(isFirst: Boolean, attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long) {}

    fun doAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long): Boolean = true

    fun postAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, manifoldId: Long) {}

    override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, hitPointWorld: com.jme3.math.Vector3f, hitNormalWorld: com.jme3.math.Vector3f, impulse: Float) {
        val attacker = o1.owner as? Entity ?: return
        (o2.owner as? Entity)?.apply {
            attackSystem.customAttack(this) {
                // TODO: CollisionHurtData 可能需要更新以包含新的碰撞信息，暂时移除 manifoldId
                this@apply.pushHurtData(CollisionHurtData(o1, o2 /*, hitPointWorld, hitNormalWorld, impulse */))
                preAttack(attackSystem.attackedEntities.isEmpty(), attacker, this@apply, o1, o2, 0L) // TODO: manifoldId 不再可用，暂时传递 0L 或考虑移除
                if (!doAttack(attacker, this@apply, o1, o2, 0L)) return@customAttack false // TODO: manifoldId 不再可用，暂时传递 0L 或考虑移除
                postAttack(attacker, this@apply, o1, o2, 0L) // TODO: manifoldId 不再可用，暂时传递 0L 或考虑移除
                true
            }
        }
    }

}