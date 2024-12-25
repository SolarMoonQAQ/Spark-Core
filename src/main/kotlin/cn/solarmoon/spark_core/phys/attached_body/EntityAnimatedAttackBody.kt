package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.entity.attack.AttackedData
import cn.solarmoon.spark_core.entity.attack.setAttackedData
import cn.solarmoon.spark_core.phys.baseCopy
import cn.solarmoon.spark_core.phys.getOwner
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.ode4j.math.DVector3
import org.ode4j.ode.DContactBuffer
import org.ode4j.ode.DGeom

/**
 * ### 跟随动画攻击Body
 * > 会在指定骨骼枢轴点上生成一个自定义大小的box来对碰撞到的实体进行基本攻击
 *
 * 特性如下：
 * - 只会在碰撞到几何体时对几何体的所有者发动攻击
 * - 攻击到目标后会给予目标一个[cn.solarmoon.spark_core.entity.attack.AttackedData]，主要储存了攻击到的body名称以及此次攻击到时所用的box
 * - 默认情况下，如果不对动画生物指定可碰撞的[AnimatedCubeBody]，那么可击打的部分和实体原生碰撞箱一致，可见[EntityBoundingBoxBody]
 * - 默认情况下，攻击根据当前所有者类型调用[Player.attack]或[net.minecraft.world.entity.LivingEntity.doHurtTarget]，不使用默认的话覆写[attack]即可
 * - 攻击将默认无视无敌时间，但可以通过[attackedEntities]来控制
 * - 默认禁用碰撞检测，在合适的节点使用[enableAttack]来启用
 * @param bodyName 该碰撞体的名称，最好不要和骨骼名重复以免冲突
 * @param boneName 指定枢轴点所在的骨骼名
 */
open class EntityAnimatedAttackBody(
    val bodyName: String,
    boneName: String,
    level: Level,
    val animatable: IEntityAnimatable<*>
): AnimatedPivotBody(boneName, level, animatable) {

    override val name: String = bodyName

    val entity = animatable.animatable

    /**
     * 单次攻击后，攻击过的生物将存入此列表，并不再触发攻击，直到调用[disableAttack]为止
     */
    val attackedEntities = mutableSetOf<Int>()

    /**
     * 是否忽略目标无敌时间
     * @see attackedEntities
     */
    var ignoreInvulnerableTime = true

    var size = DVector3()
    var offset = DVector3()
    val isEnabled get() = body.isEnabled

    init {
        body.name = bodyName
        geom.isPassFromCollide = true
        body.disable()
    }

    override fun tick() {
        super.tick()
        geom.lengths = size
        geom.offsetPosition = offset
    }

    override fun onCollide(o2: DGeom, contacts: DContactBuffer) {
        super.onCollide(o2, contacts)
        val target = o2.body.getOwner<Entity>()?.takeIf { it.id !in attackedEntities } ?: return
        attack(target, o2, contacts)
        target.setAttackedData(AttackedData(entity.id, geom, o2.body))
        attackedEntities.add(target.id)
        if (ignoreInvulnerableTime) entity.invulnerableTime = 0
    }

    open fun attack(target: Entity, o2: DGeom, contacts: DContactBuffer) {
        if (entity is Player) {
            entity.attack(target)
        } else if (entity is LivingEntity && target is LivingEntity) {
            entity.doHurtTarget(target)
        }
    }

    /**
     * 启用该攻击的碰撞检测（默认禁用）并重置攻击到的目标以忽略无敌时间
     */
    fun enableAttack() {
        if (!body.isEnabled) attackedEntities.clear()
        body.enable()
    }

    /**
     * 禁用该攻击的碰撞检测并重置攻击到的目标以忽略无敌时间
     */
    fun disableAttack() {
        body.disable()
        attackedEntities.clear()
    }

}