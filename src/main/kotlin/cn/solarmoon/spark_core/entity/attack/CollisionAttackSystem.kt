package cn.solarmoon.spark_core.entity.attack

import cn.solarmoon.spark_core.physics.body.owner
import net.minecraft.world.entity.Entity

abstract class CollisionAttackSystem(
    context: AttackContext,
    val hurtData: CollisionHurtData
): AttackSystem(context) {
    
    abstract fun preAttack(target: Entity)
    
    abstract fun doAttack(target: Entity): Boolean
    
    abstract fun postAttack(target: Entity)

    override fun onAttack(target: Entity): Boolean {
        target.hurtData.write(SparkHurtDatas.COLLISION, hurtData)
        preAttack(target)
        if (!doAttack(target)) return false
        postAttack(target)
        return true
    }

}