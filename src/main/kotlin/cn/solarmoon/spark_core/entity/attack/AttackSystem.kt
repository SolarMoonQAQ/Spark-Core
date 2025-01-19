package cn.solarmoon.spark_core.entity.attack

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import org.ode4j.ode.DGeom

/**
 * 统一的攻击方法，方便对攻击数据进行统一修改
 */
class AttackSystem(
    val entity: Entity
) {

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
    fun customAttack(target: Entity, customAction: (AttackSystem) -> Unit): Boolean {
        if (hasAttacked(target)) return false

        customAction.invoke(this)

        attackedEntities.add(target.id)
        if (ignoreInvulnerableTime) target.invulnerableTime = 0
        return true
    }

    /**
     * 同[customAttack]，但会设置碰撞相关的受击数据
     * @param customAction 在设置受击数据之后，攻击进行前插入自定义指令
     */
    fun customGeomAttack(o1: DGeom, o2: DGeom, customAction: (AttackSystem) -> Unit): Boolean {
        val target = (o2.body.owner as? Entity) ?: return false
        return customAttack(target) {
            target.pushAttackedData(AttackedData(o1, o2.body))
            customAction.invoke(this)
        }
    }

    /**
     * 常规攻击，玩家会调用[net.minecraft.world.entity.player.Player.attack]，活体则调用[net.minecraft.world.entity.LivingEntity.doHurtTarget]
     * @param actionBeforeAttack 在设置受击数据之后，攻击进行前插入自定义指令
     * @return 是否成功触发攻击指令
     */
    fun commonAttack(target: Entity, actionBeforeAttack: (AttackSystem) -> Unit = {}): Boolean {
        return customAttack(target) {
            actionBeforeAttack.invoke(this)
            when(entity) {
                is Player -> entity.attack(target)
                is LivingEntity -> entity.doHurtTarget(target)
            }
        }
    }

    /**
     * 同[commonAttack]，但会设置碰撞相关的受击数据
     * @param actionBeforeAttack 在设置受击数据之后，攻击进行前插入自定义指令
     * @return 是否成功触发攻击指令
     */
    fun commonGeomAttack(o1: DGeom, o2: DGeom, actionBeforeAttack: (AttackSystem) -> Unit = {}): Boolean {
        val target = (o2.body.owner as? Entity) ?: return false
        return commonAttack(target) {
            target.pushAttackedData(AttackedData(o1, o2.body))
            actionBeforeAttack.invoke(this)
        }
    }

    /**
     * 重置攻击到的对象等数据
     */
    fun reset() {
        attackedEntities.clear()
    }

}