package cn.solarmoon.spark_core.entity.attack

import net.minecraft.world.entity.Entity

abstract class AttackSystem<AttackContext> {

    val ignoreInvulnerableTime = true

    var resetTimes = 0

    /**
     * 是否是攻击系统创建以来的第一次攻击
     */
    val isFirstAttack get() = resetTimes == 0 && attackedEntities.isEmpty

    val attackedEntities = linkedSetOf<Entity>()

    fun hasAttacked(entity: Entity) = entity in attackedEntities

    abstract fun onAttack(target: Entity, context: AttackContext): Boolean

    fun attack(target: Entity, context: AttackContext): Boolean {
        if (hasAttacked(target)) return false

        if (onAttack(target, context)) {
            attackedEntities.add(target)
            if (ignoreInvulnerableTime) target.invulnerableTime = 0
            return true
        }

        // 如果customLogic返回false，攻击未发生
        return false
    }

    open fun reset() {
        resetTimes++
        attackedEntities.clear()
    }

}