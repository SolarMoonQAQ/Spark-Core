package cn.solarmoon.spark_core.entity.attack

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * 统一的攻击方法，方便对攻击数据进行统一修改
 */
class AttackSystem {

    /**
     * 单次攻击后，攻击过的生物将存入此列表，并不再触发攻击，直到调用[reset]为止
     */
    val attackedEntities = mutableSetOf<Int>()

    /**
     * 是否忽略目标无敌时间
     * @see attackedEntities
     */
    var ignoreInvulnerableTime = true

    /**
     * 是否已在此轮攻击中攻击过对应实体
     */
    fun hasAttacked(entity: Entity) = entity.id in attackedEntities

    /**
     * @param customAction 确定可调用攻击后的自定义指令
     * @return 是否成功触发攻击指令
     */
    fun customAttack(target: Entity, customAction: AttackSystem.() -> Boolean): Boolean {
        if (hasAttacked(target)) return false

        if (!customAction.invoke(this)) return false

        attackedEntities.add(target.id)
        if (ignoreInvulnerableTime) target.invulnerableTime = 0
        return true
    }

    fun pushCollisionData(target: Entity) {
        //target.pushAttackedData()
    }

    /**
     * 重置攻击到的对象等数据
     */
    fun reset() {
        attackedEntities.clear()
    }

}