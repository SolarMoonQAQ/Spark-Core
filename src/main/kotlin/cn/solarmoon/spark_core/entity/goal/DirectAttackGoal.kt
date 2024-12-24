package cn.solarmoon.spark_core.entity.goal

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import java.util.EnumSet

/**
 * 直接逐渐走向目标，直到两者碰撞箱距离为指定距离为止
 */
class DirectAttackGoal(private val mob: Mob, private val speedModifier: Double, private val stopDistance: Double): Goal() {

    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun tick() {
        val target = mob.target ?: return
        mob.lookControl.setLookAt(target, 30f, 30f)
        if (mob.distanceTo(target) > stopDistance) {
            mob.navigation.moveTo(target, speedModifier)
        } else {
            mob.navigation.stop()
        }
    }

    override fun requiresUpdateEveryTick(): Boolean {
        return true
    }

    override fun start() {
    }

    override fun canUse(): Boolean {
        return mob.target != null && mob.target?.isAlive == true
    }

}