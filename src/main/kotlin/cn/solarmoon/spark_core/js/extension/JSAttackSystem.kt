package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.entity.attack.AttackSystem

interface JSAttackSystem {

    val attackSystem get() = this as AttackSystem

    /**
     * 重置攻击系统，清除已攻击实体列表
     */
    fun reset() {
        attackSystem.reset()
    }

    /**
     * 检查是否已攻击过某个实体
     */
    fun hasAttacked(entityId: Int): Boolean {
        return entityId in attackSystem.attackedEntities
    }

    /**
     * 设置自动重置
     */
    fun setAutoReset(enabled: Boolean, resetAfterTicks: Int = 1) {
        if (enabled) {
            attackSystem.resetAfterTicks = resetAfterTicks
        } else {
            attackSystem.resetAfterTicks = null
        }
    }

    /**
     * 获取距离上次重置的 tick 数
     */
    fun getTicksSinceLastReset(): Int {
        return attackSystem.ticksSinceLastReset
    }

    /**
     * 获取已攻击实体数量
     */
    fun getAttackedEntityCount(): Int {
        return attackSystem.attackedEntities.size
    }

    /**
     * 设置是否忽略无敌时间
     */
    fun setIgnoreInvulnerableTime(ignore: Boolean) {
        attackSystem.ignoreInvulnerableTime = ignore
    }

} 