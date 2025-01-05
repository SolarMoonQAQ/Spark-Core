package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.entity.attack.AttackedData
import cn.solarmoon.spark_core.entity.attack.setAttackedData
import cn.solarmoon.spark_core.phys.baseCopy
import cn.solarmoon.spark_core.phys.getOwner
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.ode4j.math.DVector3
import org.ode4j.ode.DContactBuffer
import org.ode4j.ode.DGeom
import java.awt.Color

/**
 * ### 跟随动画攻击Body
 * > 会在指定骨骼枢轴点上生成一个自定义大小的box来对碰撞到的实体进行基本攻击
 *
 * 特性如下：
 * - 只会在碰撞到几何体时对几何体的所有者发动攻击
 * - 攻击到目标后会给予目标一个[cn.solarmoon.spark_core.entity.attack.AttackedData]，主要储存了攻击到的body名称以及此次攻击到时所用的box
 * - 默认情况下，如果不对动画生物指定可碰撞的[AnimatedCubeBody]，那么可击打的部分和实体原生碰撞箱一致，可见[EntityBoundingBoxBody]
 * - 默认情况下，攻击根据当前所有者类型调用[Player.attack]或[net.minecraft.world.entity.LivingEntity.doHurtTarget]，不使用默认的话覆写[onCollide]即可
 * - 默认禁用碰撞检测，在合适的节点使用[enable]来启用
 * @param bodyName 该碰撞体的名称，最好不要和骨骼名重复以免冲突
 * @param boneName 指定枢轴点所在的骨骼名
 */
open class EntityAnimatedAttackBody(
    bodyName: String,
    boneName: String,
    level: Level,
    animatable: IEntityAnimatable<*>,
    val attackSystem: AttackSystem
): AnimatedPivotBody(bodyName, boneName, level, animatable) {

    val entity = animatable.animatable

    init {
        geom.isPassFromCollide = true
        body.disable()
    }

    override fun onCollide(o2: DGeom, buffer: DContactBuffer) {
        super.onCollide(o2, buffer)

        if (getAttackAction(o2, buffer)) {
            whenAttacked(o2, buffer)
        }
    }

    open fun whenAttacked(o2: DGeom, buffer: DContactBuffer) {
        if (level.isClientSide) {
            SparkVisualEffects.GEOM.getRenderableBox(geom.uuid.toString()).setColor(Color.RED)
            SparkVisualEffects.GEOM.getRenderableBox(o2.uuid.toString()).setColor(Color.RED)
        }
    }

    open fun getAttackAction(o2: DGeom, buffer: DContactBuffer) = attackSystem.commonGeomAttack(geom, o2)

    /**
     * 启用该攻击的碰撞检测（默认禁用）并重置攻击到的目标以忽略无敌时间
     */
    override fun enable() {
        if (!body.isEnabled) attackSystem.reset()
        super.enable()
    }

    /**
     * 禁用该攻击的碰撞检测并重置攻击到的目标以忽略无敌时间
     */
    override fun disable() {
        super.disable()
        attackSystem.reset()
    }

}