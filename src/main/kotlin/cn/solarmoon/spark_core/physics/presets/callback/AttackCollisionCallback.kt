package cn.solarmoon.spark_core.physics.presets.callback

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData
import cn.solarmoon.spark_core.entity.attack.SparkHurtDatas
import cn.solarmoon.spark_core.physics.collision.CollisionCallback
import cn.solarmoon.spark_core.physics.collision.ManifoldPoint
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.Entity

interface AttackCollisionCallback: CollisionCallback {

    val attackSystem: AttackSystem

    fun preAttack(isFirst: Boolean, attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, aPoint: ManifoldPoint, bPoint: ManifoldPoint, manifoldId: Long) {}

    fun doAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, aPoint: ManifoldPoint, bPoint: ManifoldPoint, manifoldId: Long): Boolean = true

    fun postAttack(attacker: Entity, target: Entity, aBody: PhysicsCollisionObject, bBody: PhysicsCollisionObject, aPoint: ManifoldPoint, bPoint: ManifoldPoint, manifoldId: Long) {}

    override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, o1Point: ManifoldPoint, o2Point: ManifoldPoint, manifoldId: Long) {
        val attacker = o1.owner as? Entity ?: return
        (o2.owner as? Entity)?.apply {
            attackSystem.customAttack(this) {
                this@apply.hurtData.write(SparkHurtDatas.COLLISION, CollisionHurtData(o1, o2, o1Point, o2Point, manifoldId))
                preAttack(attackSystem.attackedEntities.isEmpty(), attacker, this@apply, o1, o2, o1Point, o2Point, manifoldId)
                if (!doAttack(attacker, this@apply, o1, o2, o1Point, o2Point, manifoldId)) return@customAttack false
                postAttack(attacker, this@apply, o1, o2, o1Point, o2Point, manifoldId)
                true
            }
        }
    }

}