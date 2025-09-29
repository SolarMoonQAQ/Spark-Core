package cn.solarmoon.spark_core.entity.attack

import cn.solarmoon.spark_core.physics.body.PhysicsBodyEvent
import cn.solarmoon.spark_core.physics.body.RigidBodyEntity
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.util.onEvent
import net.minecraft.world.entity.Entity

abstract class CollisionAttackSystem<C: CollisionAttackContext>(): AttackSystem<C>() {

    open fun preAttack(attacker: Entity, target: Entity, context: C) {}

    abstract fun doAttack(attacker: Entity, target: Entity, context: C): Boolean

    open fun postAttack(attacker: Entity, target: Entity, context: C) {}

    override fun onAttack(entity: Entity, context: C): Boolean {
        val attacker = context.o1.owner as? Entity ?: return false
        val target = context.o2.owner as? Entity ?: return false
        target.hurtData.write(SparkHurtDatas.COLLISION, CollisionHurtData(context.o1, context.o2, context.o1Point, context.o2Point))
        preAttack(attacker, target, context)
        if (!doAttack(attacker, target, context)) return false
        postAttack(attacker, target, context)
        return true
    }

}