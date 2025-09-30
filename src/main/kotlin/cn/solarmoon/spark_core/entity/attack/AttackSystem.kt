package cn.solarmoon.spark_core.entity.attack

import net.minecraft.world.entity.Entity

abstract class AttackSystem(
    val context: AttackContext
) {

    val ignoreInvulnerableTime = true

    abstract fun onAttack(target: Entity): Boolean

    fun attack(target: Entity): Boolean {
        if (target.level().isClientSide) return false
        if (this.context.hasAttacked(target)) return false

        if (onAttack(target)) {
            this.context.recordAttack(target)
            if (ignoreInvulnerableTime) target.invulnerableTime = 0
            return true
        }
        return false
    }

}