package cn.solarmoon.spark_core.entity.attack

import net.minecraft.world.entity.Entity

class AttackContext {

    var resetTimes = 0
    val attackedEntities = linkedSetOf<Entity>()

    val isFirstAttack get() = resetTimes == 0 && attackedEntities.isEmpty()

    fun hasAttacked(entity: Entity) = entity in attackedEntities

    fun recordAttack(entity: Entity) {
        attackedEntities.add(entity)
    }

    fun reset() {
        resetTimes++
        attackedEntities.clear()
    }

}